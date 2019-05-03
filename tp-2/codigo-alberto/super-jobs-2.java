/* -------------------------------------------------------------------------- */

package fileshare.core;

import com.sun.imageio.spi.FileImageInputStreamSpi;
import fileshare.Util;
import fileshare.transport.Endpoint;
import fileshare.transport.ReliableSocket;
import fileshare.transport.ReliableSocketConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.FileStore;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.Optional;

/* -------------------------------------------------------------------------- */

public class Things
{

    public void serve(
        ReliableSocketConnection connection,
        ExportedDirectory exportedDirectory
        )
    {
        try (connection)
        {
            final var input =
                new DataInputStream(connection.getInputStream());

            final var output =
                new DataOutputStream(connection.getOutputStream());

            // get job type

            final byte jobType = input.readByte();

            // serve job

            switch (jobType)
            {
                case JOB_ID_GET:
                    serveGet(exportedDirectory, input, output);
                    break;

                case JOB_ID_PUT:
                    servePut(exportedDirectory, input, output);
                    break;
            }
        }
        catch (Exception ignored)
        {
        }

        // remove thread from serve thread list

        this.serveThreads.remove(Thread.currentThread());
    }


}

/* -------------------------------------------------------------------------- */
