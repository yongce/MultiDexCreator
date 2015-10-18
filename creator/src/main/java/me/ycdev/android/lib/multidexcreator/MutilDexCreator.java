package me.ycdev.android.lib.multidexcreator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import me.ycdev.android.lib.multidexcreator.util.IoUtils;

public class MutilDexCreator {
    private static final String RULE_JAR_PREFIX = "jar:";
    private static final String RULE_CLASS_PREFIX = "class:";

    private final String mClassesProguardJarFile;
    private final String mProguardMappingFile;
    private final String mSecondaryDexClassesRules;
    private final String mMainDexClassesListFile;
    private final String mSecondaryDexClassesListFile;

    // mapping: originClassFilePath -> proguardClassFilePath
    private TreeMap<String, String> mMainDexClasses = new TreeMap<>();
    private TreeMap<String, String> mSecondaryDexClasses = new TreeMap<>();

    private HashSet<String> mSecondaryDexRuleClasses = new HashSet<>();
    private ArrayList<String> mSecondaryDexRulePatterns = new ArrayList<>();

    public static void main(String args[]) {
        if (args.length != 5) {
            System.out.println("Usage: java -jar MultiDexCreator.jar" +
                    " classesProguardJarFile mappingFile secondaryDexClassesRules" +
                    " mainDexClassesListFile secondaryDexClassesListFile");
            return;
        }

        try {
            MutilDexCreator creator = new MutilDexCreator(args[0], args[1], args[2], args[3], args[4]);
            creator.createMainDexClassesList();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public MutilDexCreator(String classesProguardJarFile, String proguardMappingFile,
            String secondaryDexClassesRules, String mainDexClassesListFile,
            String secondaryDexClassesListFile) {
        mClassesProguardJarFile = classesProguardJarFile;
        mProguardMappingFile = proguardMappingFile;
        mSecondaryDexClassesRules = secondaryDexClassesRules;
        mMainDexClassesListFile = mainDexClassesListFile;
        mSecondaryDexClassesListFile = secondaryDexClassesListFile;
    }

    private void createMainDexClassesList() throws IOException {
        // Step1: Load class rules in secondary dex
        loadSecondaryDexClassesRules();

        // Step 2: Load all classes
        if (new File(mProguardMappingFile).exists()) {
            // proguard enabled and obfuscate enabled
            loadProguardClasses();
        } else {
            // proguard enabled but "dontobfuscate" enabled
            ArrayList<String> allClasses = getZipClasses(mClassesProguardJarFile);
            for (String classFilePath : allClasses) {
                if (!belongsSecondaryDex(classFilePath)) {
                    mMainDexClasses.put(classFilePath, classFilePath);
                } else {
                    mSecondaryDexClasses.put(classFilePath, classFilePath);
                }
            }
        }

        // Step 3: Generate the dex classes list file
        saveDexClassesList(mMainDexClassesListFile, mMainDexClasses);
        saveDexClassesList(mSecondaryDexClassesListFile, mSecondaryDexClasses);
    }

    private void loadSecondaryDexClassesRules() throws IOException {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(mSecondaryDexClassesRules));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    // skip blank line and comment line
                    continue;
                }

                if (line.startsWith(RULE_JAR_PREFIX)) {
                    String filePath = line.substring(RULE_JAR_PREFIX.length()).trim();
                    ArrayList<String> jarClasses = getZipClasses(filePath);
                    for (String classFilePath : jarClasses) {
                        mSecondaryDexRuleClasses.add(classFilePath);
                    }
                } else if (line.startsWith(RULE_CLASS_PREFIX)) {
                    String pattern = line.substring(RULE_CLASS_PREFIX.length()).trim();
                    mSecondaryDexRulePatterns.add(pattern);
                }
            }
        } finally {
            IoUtils.close(br);
        }
    }

    private static ArrayList<String> getZipClasses(String zipFilePath) throws IOException{
        ZipFile zf = null;
        ArrayList<String> result = new ArrayList<>();
        try {
            zf = new ZipFile(zipFilePath);
            Enumeration<? extends ZipEntry> it = zf.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                String filePath = entry.getName();
                if (!entry.isDirectory() && filePath.endsWith(".class")) {
                    result.add(filePath);
                }
            }
        } finally {
            IoUtils.close(zf);
        }
        return result;
    }

    private boolean belongsSecondaryDex(String classFilePath) {
        if (mSecondaryDexRuleClasses.contains(classFilePath)) {
            return true;
        }
        for (String classPattern : mSecondaryDexRulePatterns) {
            if (classFilePath.matches(classPattern)) {
                return true;
            }
        }
        return false;
    }

    private void loadProguardClasses() throws IOException {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(mProguardMappingFile));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.endsWith(":")) {
                    int pos = line.indexOf("->");
                    String key = line.substring(0, pos).trim();
                    key = key.replace(".", "/") + ".class";
                    String value = line.substring(pos + 2).trim();
                    value = value.replace(".", "/").replace(":", ".class");
                    if (!belongsSecondaryDex(key)) {
                        mMainDexClasses.put(key, value);
                    } else {
                        mSecondaryDexClasses.put(key, value);
                    }
                }
            }
        } finally {
            IoUtils.close(br);
        }
    }

    private void saveDexClassesList(String filePath, Map<String, String> classes)
            throws IOException {
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(filePath));
            for (String classFilePath : classes.values()) {
                bw.write(classFilePath);
                bw.write('\n');
            }
            bw.flush();
        } finally {
            IoUtils.close(bw);
        }
    }
}
