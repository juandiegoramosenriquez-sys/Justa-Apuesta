package pe.justaapuesta;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JustaApuestaApplication {

	public static void main(String[] args) {
		// Las fechas se guardan en UTC; el frontend las muestra en hora de Lima
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(JustaApuestaApplication.class, args);
	}

}
