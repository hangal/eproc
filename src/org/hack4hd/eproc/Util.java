package org.hack4hd.eproc;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.logging.Log;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by hangal on 12/11/16.
 */
public class Util {
    transient private static final Parser parser = new AutoDetectParser();
    transient private static final ParseContext context = new ParseContext();

    public static boolean nullOrEmpty(String x) {
        return (x == null || "".equals(x));
    }

    public static String blobToText(String filePath) throws TikaException, SAXException, IOException {
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(-1); // no character limit
        FileInputStream fis = new FileInputStream(filePath);
        parser.parse(fis, handler, metadata, context);
        StringBuilder sb = new StringBuilder();
        sb.append (handler.toString());

        sb.append ("\nMETADATA: " + metadata.toString());
        return sb.toString();
    }

    public static Serializable readObjectFromFile(String filename) throws IOException, ClassNotFoundException
    {
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename));
        Serializable s = (Serializable) ois.readObject();
        ois.close();
        return s;
    }

    public static void writeObjectToFile(String filename, Serializable s) throws IOException
    {
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename));
        oos.writeObject(s);
        oos.close();
    }

    public static void writeStringToFile (String s, String filePath) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter (new FileOutputStream(filePath));
        pw.println (s);
        pw.close();
    }

    public static String stackTrace(Throwable t)
    {
        StringWriter sw = new StringWriter(0);
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.close();
        return sw.getBuffer().toString();
    }

    public static void print_exception(String message, Throwable t, Log log)
    {
        String trace = stackTrace(t);
        String s = message + "\n" + t.toString() + "\n" + trace;
        if (log != null)
            log.warn(s);
        System.err.println(s);
    }

    public static List<String> getInstanceFields (Object o) {
        List<String> colList = new ArrayList<>(), colNamesList = new ArrayList<>();

        Field[] fields = o.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;

            Object v = null;
            try { v = field.get(o); } catch (Exception e) { }
            String str = (v == null) ? "" : v.toString();
            colList.add(str);
        }
        return colList;
    }

    public static List<String> getInstanceFieldNames (Object o) {
        List<String> colNamesList = new ArrayList<>();

        Field[] fields = o.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;

            colNamesList.add(field.getName());
        }
        return colNamesList;
    }

    public static boolean isTextBlob (String blobName) {
        if (blobName == null)
            return false;

        blobName = blobName.toLowerCase();

        return blobName.endsWith (".doc") || blobName.endsWith (".docx")
                || blobName.endsWith (".ppt") || blobName.endsWith (".pptx")
                || blobName.endsWith (".xls") || blobName.endsWith (".xlsx")
                || blobName.endsWith (".pdf");
    }
}
