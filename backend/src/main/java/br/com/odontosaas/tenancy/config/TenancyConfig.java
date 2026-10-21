package br.com.odontosaas.tenancy.config;

import br.com.odontosaas.tenancy.support.TenantAwareDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TenancyConfig {

    /**
     * Envolve o DataSource principal da aplicação. O Flyway usa um DataSource próprio (papel de migração), criado
     * a partir de {@code spring.flyway.url}, e não passa por aqui.
     */
    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource
                        && !(bean instanceof TenantAwareDataSource)
                        && "dataSource".equals(beanName)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
