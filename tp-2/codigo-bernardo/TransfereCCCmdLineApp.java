import java.util.Scanner;

public class TransfereCCCmdLineApp implements Runnable{



    private static String mainmenu = "Welcome to the TransfereCC protocol\n+" +
                                     "To find information about the commands type \"help\"\n";

    private static String help =
                    "#############################################################" +
                    "#                                                           #" +
                    "#                           USAGE                           #" +
                    "#                                                           #" +
                    "#  connect <identifier>                                     #" +
                    "#                                                           #" +
                    "#                                                           #" +
                    "#  disconnect                                               #" +
                    "#  quit                                                     #" +
                    "#    -Disconnects automatically from every connection and   #" +
                    "#     closes the server.                                    #" +
                    "";//TODO

    @Override
    public void run(){
        TransfereCC tcc = new TransfereCC();
        String cmd = null;
        Scanner scan = new Scanner(System.in);
        System.out.println(mainmenu);

        cmd = scan.nextLine();
        while(!cmd.equals("quit")){


            cmd = scan.nextLine();
        }


    }

    public static void main(String[] args){
        TransfereCCCmdLineApp cla = new TransfereCCCmdLineApp();
        Thread clat = new Thread(cla);

        Server s = new Server();
        Thread st = new Thread(s);






    }
}
