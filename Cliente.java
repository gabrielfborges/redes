import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {

    static final int PORTA_SERVIDOR = 9990;

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        System.out.print("IP do servidor (Enter = localhost): ");
        String ipServidor = sc.nextLine().trim();
        if (ipServidor.isEmpty()) ipServidor = "localhost";

        Socket socket = new Socket(ipServidor, PORTA_SERVIDOR);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        in.readLine(); // servidor pergunta o nick
        System.out.print("Digite seu nickname: ");
        String nick = sc.nextLine().trim();

        ServerSocket p2p = new ServerSocket(0);
        DatagramSocket udp = new DatagramSocket(); 

        out.println(nick + ";" + p2p.getLocalPort() + ";" + udp.getLocalPort());
        String resp = in.readLine();
        if (!resp.equals("OK")) {
            System.out.println(resp);
            return;
        }
        System.out.println("Conectado como " + nick);

        new Thread(() -> receberGrupo(udp)).start();
        new Thread(() -> aceitarPrivado(p2p)).start();

        while (true) {
            System.out.println("\n1 - Listar contatos");
            System.out.println("2 - Conversa privada (TCP)");
            System.out.println("3 - Conversa em grupo (UDP)");
            System.out.println("0 - Sair");
            String op = sc.nextLine().trim();

            if (op.equals("1")) {
                String[] lista = listar(in, out);
                System.out.println("--- Contatos cadastrados ---");
                for (int i = 1; i < lista.length; i++) {
                    String[] c = lista[i].split(",");
                    System.out.println(c[0] + "  IP=" + c[1] + "  Porta=" + c[2]);
                }

            } else if (op.equals("2")) {
                System.out.print("Nick do destino: ");
                String destino = sc.nextLine().trim();

                // procura o destino na lista pedida ao servidor
                String[] lista = listar(in, out);
                String[] alvo = null;
                for (int i = 1; i < lista.length; i++) {
                    String[] c = lista[i].split(",");
                    if (c[0].equals(destino)) alvo = c;
                }
                if (alvo == null || destino.equals(nick)) {
                    System.out.println("Contato nao encontrado.");
                    continue;
                }

                // abre conexao TCP direta
                Socket priv = new Socket(alvo[1], Integer.parseInt(alvo[2]));
                PrintWriter w = new PrintWriter(priv.getOutputStream(), true);
                System.out.println("Conversa privada com " + destino + ". Digite /sair para voltar.");
                while (true) {
                    String texto = sc.nextLine();
                    if (texto.equals("/sair")) break;
                    w.println(nick + ": " + texto);
                }
                priv.close();

            } else if (op.equals("3")) {
                System.out.println("Modo grupo. Digite /sair para voltar.");
                InetAddress servidor = InetAddress.getByName(ipServidor);
                while (true) {
                    String texto = sc.nextLine();
                    if (texto.equals("/sair")) break;
                    byte[] dados = (nick + ": " + texto).getBytes();
                    udp.send(new DatagramPacket(dados, dados.length, servidor, PORTA_SERVIDOR));
                }

            } else if (op.equals("0")) {
                out.println("SAIR");
                System.exit(0);
            }
        }
    }

    // Pede a lista ao servidor
    static String[] listar(BufferedReader in, PrintWriter out) throws Exception {
        out.println("LISTA");
        return in.readLine().split(";");
    }

    // Fica recebendo mensagens do grupo
    static void receberGrupo(DatagramSocket udp) {
        try {
            byte[] buf = new byte[1024];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                udp.receive(p);
                System.out.println(new String(p.getData(), 0, p.getLength()));
            }
        } catch (Exception e) {
        }
    }

    // Fica esperando outros clientes abrirem conversa privada
    static void aceitarPrivado(ServerSocket p2p) {
        try {
            while (true) {
                Socket c = p2p.accept();
                new Thread(() -> lerPrivado(c)).start();
            }
        } catch (Exception e) {
        }
    }

    // Le e mostra as mensagens de uma conversa privada
    static void lerPrivado(Socket c) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String linha;
            while ((linha = r.readLine()) != null) {
                System.out.println("[PRIVADO] " + linha);
            }
            c.close();
        } catch (Exception e) {
        }
    }
}
