package org.hack4hd.eproc;

import org.apache.commons.io.FilenameUtils;

/**
 * Created by hangal on 12/12/16.
 */
public class FileNameManager {

    StringManager stringManager = new StringManager();

    /** like string manager's method, but keeps extensions intact */
    public synchronized String registerAndDisambiguate(String fileName) {
        String baseName = FilenameUtils.getBaseName(fileName);
        String extension = FilenameUtils.getExtension(fileName);
        if (Util.nullOrEmpty(baseName) || Util.nullOrEmpty(extension)) {
            baseName = fileName;
            extension = "";
        }

        String newFileName = stringManager.registerAndDisambiguate(baseName);

        if (!Util.nullOrEmpty(extension)) {
            newFileName += ("." + extension);
        }

        return newFileName;
    }
}
