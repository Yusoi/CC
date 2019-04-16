import java.io.*;
import java.net.DatagramPacket;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TransfereCC {

    private ConcurrentHashMap<Long,Estado> estado;


    public String sendFile(File file){
        if(!file.isFile()){
            return "Path does not refer to a file.";
        }

        try {
            Set<File> fileSet = splitFile(file);


            Iterator<File> it = fileSet.iterator();

            while(it.hasNext()){
                AgenteUDP audp = new AgenteUDP(estado, (File) it.next());
                //TODO gerar threads
            }

        }catch(IOException e){
            return e.getMessage();
        }

        try {
            long size = file.length();
        }catch(SecurityException e) {
            return e.getMessage();
        }



        return "File sent successfully";
    }

    public Set<File> splitFile(File f) throws IOException{
        int partCounter = 0;

        int splitSize = 1024 * 1024; //1MB por exemplo
        byte[] buffer = new byte[splitSize];

        String filename = f.getName();

        HashSet<File> files = new HashSet<File>();

        try(FileInputStream fis = new FileInputStream(f);
            BufferedInputStream bis = new BufferedInputStream(fis)){

            int byteAmount = 0;

            while((byteAmount = bis.read(buffer)) > 0){
                String filePartName = String.format("%s.%03d",filename,partCounter++);
                File newFile = new File(f.getParent(), filePartName);
                try(FileOutputStream out = new FileOutputStream(newFile)){
                    out.write(buffer,0,byteAmount);
                }

            }
        }

        return files;
    }
}
