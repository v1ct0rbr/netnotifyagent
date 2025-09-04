package br.gov.pb.der.netnotifyagent;

import java.io.IOException;
import java.util.Properties;

import br.gov.pb.der.netnotifyagent.utils.Functions;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RabbitmqService {

    private static final String RESOURCES_PATH = "resources/";
    private static final String SETTINGS_FILE = RESOURCES_PATH + "settings.properties";

    private String host;
    private String username;
    private String password;
    private String exchangeName;

    Properties settings;

    public RabbitmqService() {
        try {
            this.settings = Functions.loadProperties(SETTINGS_FILE);
            this.host = settings.getProperty("rabbitmq.host");
            this.username = settings.getProperty("rabbitmq.username");
            this.password = settings.getProperty("rabbitmq.password");
            this.exchangeName = settings.getProperty("rabbitmq.exchange");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
