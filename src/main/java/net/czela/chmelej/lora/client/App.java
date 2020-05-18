package net.czela.chmelej.lora.client;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ivy.util.HexEncoder;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.thethingsnetwork.data.common.Connection;
import org.thethingsnetwork.data.common.Metadata;
import org.thethingsnetwork.data.common.messages.ActivationMessage;
import org.thethingsnetwork.data.common.messages.DataMessage;
import org.thethingsnetwork.data.common.messages.UplinkMessage;
import org.thethingsnetwork.data.mqtt.Client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Hello world!
 * <p>
 * <p>
 create table lora_message (
 id 				integer primary key AUTO_INCREMENT,
 received_when		TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 app_Id			varchar(100) not null,
 dev_Id			varchar(100) not null,
 hardware_Serial	varchar(100),
 is_Retry			varchar(10),
 port				integer,
 counter			integer,
 payloadRaw		varchar(100)
 alter table lora_message add(
 meta_time varchar(100),
 meta_frequency double,
 meta_modulation varchar(100),
 meta_data_Rate varchar(100),
 meta_bit_Rate varchar(100),
 meta_coding_Rate varchar(100)
 );

 SELECT * FROM lora_message

 create table lora_message_gateway (
 id 		integer primary key AUTO_INCREMENT,
 msg_id 	integer not null, -- FK lora_message(id)
 gtw_Id varchar(100),
 gtw_timestamp integer,
 gtw_time varchar(100),
 gtw_channel integer,
 gtw_rssi double,
 gtw_snr double,
 gtw_rfChain integer,
 gtw_latitude double,
 gtw_longitude double,
 gtw_altitude double
 )
 * );
 */
public class App {
    static SqlSessionFactory sqlSessionFactory;

    static void initDb() throws IOException {
        String resource = "mybatis-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        //session = sqlSessionFactory.openSession();

        //Date now = session.selectOne("lora.now");
        //System.out.println("now = " + now);
    }

    public static void main(String[] args) {
        String region = "eu";
        String appId = "dragino-tracker"; 
        String accessKey = "ttn-account-v2.4E__vZX_PqqjMDA4vden-_Cae_Z2tLxLADGyWKGlOlg"; 

        try {
            initDb();

            Client client = new Client(region, appId, accessKey);

            client.onConnected((Connection _client) ->
                    System.out.println("Connected !"));

            client.onError((Throwable _error) ->
                    System.err.println("Error: " + _error.getMessage()));

            client.onActivation((String _devId, ActivationMessage _data) ->
                    System.out.println("Activation: " + _devId + ", data: " + _data));

            client.onMessage((String devId, DataMessage data) ->
                    onMessage(data)
            );

            client.start();

        } catch (URISyntaxException | MqttException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void onMessage(DataMessage data) {
        System.out.println("Message: " + toString(data));

        SqlSession session = sqlSessionFactory.openSession();
        try {

            if (data instanceof UplinkMessage) {
                UplinkMessage m = (UplinkMessage) data;
                Metadata meta = m.getMetadata();
                Map<String, Object> map = new HashMap<>();
                map.put("appId", m.getAppId());
                map.put("devId", m.getDevId());
                map.put("hardwareSerial", m.getHardwareSerial());
                map.put("isRetry", m.isRetry());
                map.put("port", m.getPort());
                map.put("counter", m.getCounter());
                map.put("payloadRaw", HexEncoder.encode(m.getPayloadRaw()));
                map.put("meta_time", meta.getTime());
                map.put("meta_frequency", meta.getFrequency());
                map.put("meta_modulation", meta.getModulation());
                map.put("meta_data_rate", meta.getDataRate());
                map.put("meta_bit_rate", meta.getBitRate());
                map.put("meta_coding_rate", meta.getCodingRate());

                session.insert("lora.insertMessage", map);
                long id = session.selectOne("lora.lastId");
                for (Metadata.Gateway gw : meta.getGateways()) {
                    Map<String, Object> map2 = new HashMap<>();
                    map2.put("msg_id", id);
                    map2.put("gtw_id", gw.getId());
                    map2.put("gtw_timestamp", gw.getTimestamp());
                    map2.put("gtw_time", gw.getTime());
                    map2.put("gtw_channel", gw.getChannel());
                    map2.put("gtw_rssi", gw.getRssi());
                    map2.put("gtw_snr", gw.getSnr());
                    map2.put("gtw_rfchain", gw.getRfChain());
                    map2.put("gtw_latitude", gw.getLatitude());
                    map2.put("gtw_longitude", gw.getLongitude());
                    map2.put("gtw_altitude", gw.getAltitude());
                    session.insert("lora.insertMessageGtw", map2);
                }

                Map<String, Object> map3 = new HashMap<>();
                map3.putAll(m.getPayloadFields());
                map3.put("msg_id", id);
                session.insert("lora.insertMessageTracker", map3);
            }
            session.commit();
        } catch (Exception e ) {
            session.rollback();
            throw e;
        }
        session.close();
    }

    private static String toString(DataMessage dm) {
        if (dm instanceof UplinkMessage) {
            UplinkMessage m = (UplinkMessage) dm;
            StringBuilder sb = new StringBuilder();
            m.getPayloadFields().forEach((key, value) -> sb.append(key + ":" + value + ", "));
            return "UplinkMessage{" +
                    "appId='" + m.getAppId() + '\'' +
                    ", devId='" + m.getDevId() + '\'' +
                    ", hardwareSerial='" + m.getHardwareSerial() + '\'' +
                    ", isRetry=" + m.isRetry() +
                    ", port=" + m.getPort() +
                    ", counter=" + m.getCounter() +
                    ", payloadRaw='" + HexEncoder.encode(m.getPayloadRaw()) + '\'' +
                    ", payloadMap='" + sb.toString() + '\'' +
                    '}';
        } else {
            return dm.toString();
        }
    }

    /*
    //The function is :
    static Map<String,Object> decoder(byte[] payload) {
        long value = hex2long(payload, 0);
        double latitude=1.0*value/10000;                   // gps latitude,units:

        value = hex2long(payload, 3);
        double longitude=1.0*value/10000;                  // gps longitude,units:

        String alarm=((payload[6] & 0x40) > 0)?"T":"F";

        value=((payload[6] & 0x3f) <<8) | payload[7];
        double batV=1.0*value/1000;                        // Battery,units:V

        value=payload[8]<<8 | payload[9];
        if ((payload[8] & 0x80) > 0) {
            value |=0xFFFF0000;
        }
        double roll=1.0*value/100;                         // roll,units:

        value=payload[10]<<8 | payload[11];
        if((payload[10] & 0x80)>1) {
            value |=0xFFFF0000;
        }
        double pitch=1.0*value/100;                        // pitch,units:

        Map<String,Object> map = new HashMap<>();
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("roll", roll);
        map.put("pitch", pitch);
        map.put("batv", batV);
        map.put("alarm_status", alarm);
        return map;
    }

    static long hex2long(byte[] bytes, int offset) {
        long value = bytes[offset+0]<<16 | bytes[offset+1]<<8 | bytes[offset+2];
        if((bytes[offset+0] & 0x80) > 0) {
            value = value | 0xFF000000;
        }
        return value;

    }
    */
}
