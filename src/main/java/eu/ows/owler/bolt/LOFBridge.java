package eu.ows.owler.bolt;
import py4j.GatewayServer;
import eu.ows.owler.bolt.LOFInterface;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LOFBridge {

    public static void main(String[] args) {
        try {
            GatewayServer gateway = new GatewayServer();
            gateway.start();
            LOFInterface lof = (LOFInterface) gateway.getPythonServerEntryPoint(new Class[] {LOFInterface.class});
            double[] embedding = new double[]{-0.2354707270860672, 0.25799551606178284, -0.1670743227005005, -0.012231017462909222, 0.16397954523563385, 0.2882660925388336};
            ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES * embedding.length);
            for (int i = 0; i < embedding.length; i++) {
                buffer.putDouble(embedding[i]);
            }
            String prediction = lof.predict(buffer.array());
            System.out.println("JAVA: " +prediction);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
