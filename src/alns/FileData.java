package alns;

import alns.data.Data;
import alns.param.Parameters;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * This class is for testing purposes only. It implements methods for
 * serializing and deserializing a Data object to/from file.
 *
 * @author Markov
 * @version 1.0
 */
public class FileData {

    /**
     * Serializes Data object to file.
     *
     * @param data Data object
     * @param folder folder
     * @param fileName file name
     * @throws java.io.FileNotFoundException
     */
    public static void ExportData(Data data, String folder, String fileName) throws FileNotFoundException, IOException {

        FileOutputStream fileOut = new FileOutputStream(folder + fileName);
        ObjectOutputStream out = new ObjectOutputStream(fileOut);
        out.writeObject(data);
        out.close();
        fileOut.close();
        System.out.println("Serialized Data object in " + fileName + "...");
    }

    /**
     * Deserializes Data object from file.
     *
     * @param folder folder
     * @param fileName file name
     * @return Data object
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     *
     */
    public static Data ReadData(String folder, String fileName) throws FileNotFoundException, IOException, ClassNotFoundException {
   
        FileInputStream fileIn = new FileInputStream(folder + fileName);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        Data data = (Data) in.readObject();
        in.close();
        fileIn.close();
        System.out.println("Deserialized Data object from " + fileName + "...");
        System.out.println("Returning Data object...");

        return data;
    }
}
