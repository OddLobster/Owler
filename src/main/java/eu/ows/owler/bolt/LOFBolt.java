package eu.ows.owler.bolt;
import py4j.GatewayServer;
import eu.ows.owler.bolt.LOFInterface;

import java.nio.ByteBuffer;

public class LOFBolt {


    public byte[] getByteArray(double[] embedding) {
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES * embedding.length);
        for (int i = 0; i < embedding.length; i++) {
            buffer.putDouble(embedding[i]);
        }
        return buffer.array();
    }

    public static void main(String[] args) {
        try {
            GatewayServer gateway = new GatewayServer();
            gateway.start();
            LOFInterface lof = (LOFInterface) gateway.getPythonServerEntryPoint(new Class[] {LOFInterface.class});
            // byte[] modelInput = getByteArray(new double[]{-0.2354707270860672, 0.25799551606178284, -0.1670743227005005, -0.012231017462909222, 0.16397954523563385, 0.2882660925388336});
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
