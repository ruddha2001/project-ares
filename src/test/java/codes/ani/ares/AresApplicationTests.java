package codes.ani.ares;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=" +
				"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
				"org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
				"com.google.cloud.spring.autoconfigure.storage.GcpStorageAutoConfiguration," +
				"com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration"
})
class AresApplicationTests {

	@Test
	void contextLoads() {
	}

}
