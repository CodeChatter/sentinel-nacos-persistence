package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class NacosConfig {

    @Value("${nacos.address}")
    private String address;

    @Value("${nacos.namespace}")
    private String namespace;

    @Bean
    public ConfigService nacosConfigService() throws Exception {
        Properties properties = new Properties();
        //nacos集群地址
        properties.put(PropertyKeyConst.SERVER_ADDR, address);
        //namespace为空即为public
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
        return ConfigFactory.createConfigService(properties);
    }
}
 