package study.yume;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class YumeApplication {

	public static void main(String[] args) {
		SpringApplication.run(YumeApplication.class, args);
	}

}
