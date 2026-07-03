package es.vodafone.sim.snmp.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

  @Bean
  public OpenAPI sidSnmpAgentOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("SID-SnmpAgent API")
            .description("API REST para gestión dinámica de agentes SNMPv3 y sus interfaces")
            .version("1.0.0")
            .contact(new Contact()
                .name("Vodafone España — Network Engineering")
                .email("rlopezb@vodafone.com")));
  }
}