package com.project.handongjudge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        Server server = new Server();


        server.setUrl("/");

        List<Server> servers = new ArrayList<>();
        servers.add(server);

        return new OpenAPI()
                .servers(servers)
                .info(new Info()
                        .title("HandongJudge")
                        .description("HandongJudge REST API 문서")
                        .version("v1.0.0"));
    }
}
