package tn.fastcoloc;

import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** DynamicPropertySource in database tests must not hide missing production datasource bindings. */
class ConfigurationContractTest {
    @Test void packagedDatasourceConfigurationUsesExternalBindingsAndContainsNoLocalCredentialValues() throws Exception {
        Properties config=new Properties();
        try(var stream=getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(stream,"Packaged application configuration is required");
            config.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }
        for(String key:new String[]{"spring.datasource.url","spring.datasource.username","spring.datasource.password"}) {
            String value=config.getProperty(key,"").trim();
            // Boolean assertions intentionally avoid printing a credential if this check fails.
            assertTrue(value.startsWith("${")&&value.endsWith("}"),"Datasource bindings must resolve external configuration: "+key);
        }
        for(String key:new String[]{"FASTCOLOC_DB_PASSWORD","FASTCOLOC_DB_USERNAME","FASTCOLOC_JDBC_URL"})
            assertFalse(config.containsKey(key),"Local values must not be packaged as application properties: "+key);
        assertTrue(config.getProperty("spring.config.import","").contains(".env.local"),"Local configuration import must remain explicit");
    }
}
