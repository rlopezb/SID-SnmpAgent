package es.vodafone.sim.snmp;

import org.snmp4j.log.JavaLogFactory;
import org.snmp4j.log.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SnmpAgentApplication {
  static {
    LogFactory.setLogFactory(new JavaLogFactory());
  }

  public static void main(String[] args) {
    SpringApplication.run(SnmpAgentApplication.class, args);
  }
}
