import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Servidor {

    static final int PORTA = 9990;

    static ArrayList<Contato> contatos = new ArrayList<>();

    static class Contato {
        String nick;
        String ip;
        int portaTcp;
        int portaUdp;

        Contato(String nick, String ip, int portaTcp, int portaUdp) {
            this.nick = nick;
            this.ip = ip;
            this.portaTcp = portaTcp;
            this.portaUdp = portaUdp;
        }
    }

    public static void main(String[] args) throws Exception {
        new Thread(() -> broadcastUdp()).start();

        ServerSocket server = new ServerSocket(PORTA);
        System.out.println("Servidor rodando na porta " + PORTA + " (TCP e UDP)");

        while (true) {
            Socket s = server.accept();
            new Thread(() -> atender(s)).start();
        }
    }

    static void atender(Socket s) {
        Contato eu = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter out = new PrintWriter(s.getOutputStream(), true);

            out.println("NICK?");
            String[] dados = in.readLine().split(";");
            String nick = dados[0];
            String ip = s.getInetAddress().getHostAddress();
            int portaTcp = Integer.parseInt(dados[1]);
            int portaUdp = Integer.parseInt(dados[2]);

            synchronized (contatos) {
                for (Contato c : contatos) {
                    if (c.nick.equals(nick)) {
                        out.println("ERRO: nickname ja esta em uso");
                        s.close();
                        return;
                    }
                }
                eu = new Contato(nick, ip, portaTcp, portaUdp);
                contatos.add(eu);
            }
            out.println("OK");
            System.out.println("Entrou: " + nick + " " + ip);

            String msg;
            while ((msg = in.readLine()) != null) {
                if (msg.equals("LISTA")) {
                    String lista = "LISTA";
                    synchronized (contatos) {
                        for (Contato c : contatos) {
                            lista = lista + ";" + c.nick + "," + c.ip + "," + c.portaTcp;
                        }
                    }
                    out.println(lista);
                } else if (msg.equals("SAIR")) {
                    break;
                }
            }
            s.close();
        } catch (Exception e) {
            // cliente caiu
        }

        if (eu != null) {
            synchronized (contatos) {
                contatos.remove(eu);
            }
            System.out.println("Saiu: " + eu.nick);
        }
    }

    static void broadcastUdp() {
        try {
            DatagramSocket udp = new DatagramSocket(PORTA);
            byte[] buf = new byte[1024];

            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                udp.receive(p);

                String msg = "[GRUPO] " + new String(p.getData(), 0, p.getLength());
                byte[] dados = msg.getBytes();

                synchronized (contatos) {
                    for (Contato c : contatos) {
                        DatagramPacket envio = new DatagramPacket(dados, dados.length,
                                InetAddress.getByName(c.ip), c.portaUdp);
                        udp.send(envio);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
