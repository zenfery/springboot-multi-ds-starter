package cc.zenfery.boot.autoconfigure.mds;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import lombok.extern.apachecommons.CommonsLog;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import cc.zenfery.boot.autoconfigure.mds.annotation.Mds;
import cc.zenfery.boot.autoconfigure.mds.aop.MdsAdvice;
import cc.zenfery.boot.autoconfigure.mds.aop.MdsAnnotationMethodMatcher;
import cc.zenfery.boot.autoconfigure.mds.aop.MdsPointcut;

/**
 * Multi DataSource AutoConfiguration
 * 
 * @author zenfery
 *
 */
@EnableConfigurationProperties(MdsProperties.class)
@ConditionalOnMissingBean(MdsAutoConfiguration.class)
@ConditionalOnProperty(prefix = MdsProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
// @EnableAspectJAutoProxy(proxyTargetClass = true)
@Configuration
@CommonsLog
public class MdsAutoConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSourceProperties dataSourceProperties;

    @Autowired
    private MdsProperties mdsProperties;

    @Bean
    @Primary
    @ConditionalOnMissingBean(AbstractRoutingDataSource.class)
    public DataSource mdsRoutingDataSource() {

        MdsRoutingDataSource mdsRoutingDataSource = null;
        DataSource defaultDataSource = null;

        Map<Object, Object> dataSourceMap = null;

        // => fetch the default DataSource from Spring Context
        Map<String, DataSource> dataSourcesInIOC = BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext,
                DataSource.class);
        if (dataSourcesInIOC != null && dataSourcesInIOC.size() > 1) {
            String errorMsg = "config for [spring.datasource] is not only one.";
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        if (dataSourcesInIOC != null && dataSourcesInIOC.size() == 1) {
            if (dataSourceMap == null) {
                dataSourceMap = new HashMap<Object, Object>();
            }
            defaultDataSource = dataSourcesInIOC.get(dataSourceProperties.getName());
            dataSourceMap.put(dataSourceProperties.getName(), defaultDataSource);
        } else {
            mdsProperties.getDatasources().add(dataSourceProperties);
        }

        // => create multi DataSource
        List<DataSourceProperties> dataSources = mdsProperties.getDatasources();
        if (!dataSources.isEmpty()) {

            if (dataSourceMap == null) {
                dataSourceMap = new HashMap<Object, Object>(dataSources.size());
            }

            for (DataSourceProperties dsp : dataSources) {
                DataSourceBuilder builder = DataSourceBuilder.create(dsp.getClassLoader())
                        .driverClassName(dsp.getDriverClassName()).url(dsp.getUrl()).username(dsp.getUsername())
                        .password(dsp.getPassword());
                if (dsp.getType() != null) {
                    builder.type(dsp.getType());
                }
                DataSource dataSource = builder.build();
                if (dsp.getName() != null && dataSourceProperties.getName() != null
                        && dsp.getName().equals(dataSourceProperties.getName())) {
                    defaultDataSource = dataSource;
                }
                dataSourceMap.put(dsp.getName(), dataSource);
            }

        }

        // new MdsRoutingDataSource
        if (dataSourceMap != null && !dataSourceMap.isEmpty()) {
            mdsRoutingDataSource = new MdsRoutingDataSource();
            mdsRoutingDataSource.setTargetDataSources(dataSourceMap);
            if (defaultDataSource != null) {
                mdsRoutingDataSource.setDefaultTargetDataSource(defaultDataSource);
            }
        }

        return mdsRoutingDataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public MethodInterceptor mdsAdvice() {
        MdsAdvice dataSourceAdvice = new MdsAdvice();
        dataSourceAdvice.setOrder(Ordered.LOWEST_PRECEDENCE);
        dataSourceAdvice.setDefaultDataSourceName(dataSourceProperties.getName());
        return dataSourceAdvice;
    }

    @Bean
    @ConditionalOnMissingBean
    public MethodMatcher mdsMethodMatcher() {
        MdsAnnotationMethodMatcher mm = new MdsAnnotationMethodMatcher(Mds.class);
        return mm;
    }

    @Bean
    @ConditionalOnMissingBean
    public Pointcut mdsPointcut(MethodMatcher mdsMethodMatcher) {
        MdsPointcut mdsPointcut = new MdsPointcut();
        mdsPointcut.setMethodMatcher(mdsMethodMatcher);
        return mdsPointcut;
    }

    @Bean
    @ConditionalOnMissingBean
    public PointcutAdvisor mdsPointcutAdvisor(@Qualifier("mdsAdvice") MethodInterceptor mdsAdvice,
            @Qualifier("mdsPointcut") Pointcut mdsPointcut) {
        DefaultBeanFactoryPointcutAdvisor advisor = new DefaultBeanFactoryPointcutAdvisor();
        advisor.setPointcut(mdsPointcut);
        advisor.setAdvice(mdsAdvice);
        return advisor;
    }

}