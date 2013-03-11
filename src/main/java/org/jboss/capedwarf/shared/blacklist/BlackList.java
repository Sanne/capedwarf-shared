package org.jboss.capedwarf.shared.blacklist;

/**
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.apphosting.runtime.security.WhiteList;

/**
 * Black list
 *
 * @author rudominer@google.com
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
public class BlackList {
    private static final Logger log = Logger.getLogger(BlackList.class.getName());
    private static Set<String> blackList = new HashSet<String>();

    static {
        initBlackList();
    }

    public static Set<String> getBlackList() {
        return blackList;
    }

    private static void initBlackList() {
        Set<File> jreJars = getCurrentJreJars();

        for (File f : jreJars) {
            JarFile jarFile;
            try {
                jarFile = new JarFile(f);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Unable to read a jre library while constructing the blacklist. Security restrictions may not be entirely emulated. " + f.getAbsolutePath());
                continue;
            }

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.endsWith(".class") == false) {
                    continue;
                }
                String className = entryName.replace('/', '.').substring(0, entryName.length() - ".class".length());
                if (isBlackListed(className)) {
                    blackList.add(className);
                }
            }
        }

        blackList = Collections.unmodifiableSet(blackList);
    }

    @SuppressWarnings("RedundantIfStatement")
    private static boolean isBlackListed(String className) {
        Set<String> whiteList = WhiteList.getWhiteList();
        if (whiteList.contains(className)) {
            return false;
        }
        if (className.startsWith("com.sun.xml.internal.bind.")) {
            return false;
        }
        if (className.startsWith("java.io") || className.startsWith("java.nio")) {
            return false;
        }
        return true;
    }

    private static Set<File> getCurrentJreJars() {
        return getJreJars(System.getProperty("java.home"));
    }

    private static Set<File> getJreJars(String jreHome) {
        Set<File> matchingFiles = new HashSet<File>();

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        };

        getFilesRecursive(matchingFiles, new File(jreHome + File.separator + "lib"), filter);

        if (isOSX()) {
            File classes = new File(jreHome + File.separator + ".." + File.separator + "Classes");
            if (classes.exists()) {
                // Classes might not exists; e.g. fixed in JDK7
                getFilesRecursive(matchingFiles, classes, filter);
            }
        }

        return matchingFiles;
    }

    private static void getFilesRecursive(final Set<File> matchingFiles, final File dir, final FilenameFilter filter) {
        File[] files = dir.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                getFilesRecursive(matchingFiles, f, filter);
            } else if (filter.accept(dir, f.getName())) {
                matchingFiles.add(f);
            }
        }
    }

    private static boolean isOSX() {
        String osName = System.getProperty("os.name");
        return osName.contains("OS X");
    }
}
