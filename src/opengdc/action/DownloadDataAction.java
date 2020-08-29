package opengdc.action;

import opengdc.GUI;
import opengdc.Settings;
import opengdc.util.DataExtractionTool;
import opengdc.util.FSUtils;
import opengdc.util.GDCQuery;
import java.io.File;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.JTextPane;

/**
 *
 * @author fabio
 */
public class DownloadDataAction extends Action {
    
    private static JTextPane logPane = null;

    @Override
    public void execute(String[] args) {
        // skip the first entry -> download
        //String action = args[0];
        //String program = args[1];
        String disease = args[2];
        String dataType = args[3];
        boolean autoextract = Boolean.valueOf(args[4]);
        boolean autoremove = Boolean.valueOf(args[5]);
        String gdc_path = Settings.getOutputGDCFolder();
        
        // create log window
        logPane = GUI.createLogWindow();
        System.out.println("DOWNLOAD\tDisease: "+disease+"\tDataType: "+dataType);
        System.err.println("Downloading GDC Data" + "\n" + "Disease: " + disease + "\n" + "Data Type: " + dataType + "\n" + "Output Folder Path: " + gdc_path + "\n" + "Auto-extract: " + autoextract + "\n" + "Auto-remove: " + autoremove + "\n");
        GUI.appendLog(logPane, "Downloading GDC Data" + "\n" + "Disease: " + disease + "\n" + "Data Type: " + dataType + "\n" + "Output Folder Path: " + gdc_path + "\n" + "Auto-extract: " + autoextract + "\n" + "Auto-remove: " + autoremove + "\n");
        
        if (dataType.trim().toLowerCase().contains("clinical") || dataType.trim().toLowerCase().contains("biospecimen")) {
            if (dataType.trim().toLowerCase().contains("clinical"))
                retrieveData(disease, "Clinical Supplement", gdc_path, autoextract, autoremove);
            if (dataType.trim().toLowerCase().contains("biospecimen"))
                retrieveData(disease, "Biospecimen Supplement", gdc_path, autoextract, autoremove);
        }
        else
            retrieveData(disease, dataType, gdc_path, autoextract, autoremove);
        
        System.err.println("\n" + "done" + "\n\n" + "#####################" + "\n\n");
        GUI.appendLog(logPane, "\n" + "done" + "\n\n" + "#####################" + "\n\n");
    }
    
    private void retrieveData(String disease, String dataType, String gdc_path, boolean autoextract, boolean autoremove) {
        String query_file_path = GDCQuery.query(disease, dataType, 0);
        HashMap<String, HashMap<String, String>> dataMap = GDCQuery.extractInfo(query_file_path);
        GUI.appendLog(logPane, "Data Amount: " + dataMap.size() + " files" + "\n\n");
        
        // TODO activate progress bar
        
        /* DOWNLOAD (AND EXTRACT (AND REMOVE)) FILE BY FILE */
        int progress_counter = 1;
        for (String uuid: dataMap.keySet()) {
            GUI.appendLog(logPane, "Processing entry: " + progress_counter + "/" + dataMap.size() + "\n");
            downloadSingleData(uuid, dataMap, gdc_path, autoextract, autoremove);
            progress_counter++;
        }
    }
    
    public static void downloadSingleData(String uuid, HashMap<String, HashMap<String, String>> dataMap, String gdc_path, boolean autoextract, boolean autoremove) {
        // download data
        String fileName = uuid + "_" + dataMap.get(uuid).get("file_name");
        File data_file = new File(gdc_path + "/" + fileName);
        if (!data_file.exists()) {
            for (String s: dataMap.get(uuid).keySet())
                System.err.println(s + "\t" + dataMap.get(uuid).get(s));

            GDCQuery.downloadFile(uuid, gdc_path, fileName, false, 0, logPane);
            if (autoextract) {
                HashSet<String> uncompressed_folders_path = new HashSet<>();
                HashSet<String> experiments_path = new HashSet<>();
                if (data_file.exists()) {
                    String destDirPath = gdc_path + "/" + data_file.getName() + "_/";
                    File destDir = new File(destDirPath);

                    // extract data
                    boolean uncompressed = DataExtractionTool.uncompressData(data_file, destDir, false, true);
                    //System.out.println("uncompressed: " + uncompressed);
                    uncompressed_folders_path.addAll(DataExtractionTool.getUncompressedFoldersPathList());
                    experiments_path.addAll(DataExtractionTool.getExperimentsPathList());

                    // copy experiments to the gdc_path folder
                    for (String exp: experiments_path) {
                        try {
                            File expFile = new File(exp);
                            if (!gdc_path.endsWith("/"))
                                gdc_path += "/";
                            File newExpFile = new File(gdc_path + expFile.getName());
                            Files.move(expFile.toPath(), newExpFile.toPath(), REPLACE_EXISTING);
                        }
                        catch (Exception e) {
                            //e.printStackTrace();
                        }
                    }

                    // remove other files and folders
                    for (String dir: uncompressed_folders_path) {
                        File dirFolder = new File(dir);
                        FSUtils.deleteDir(dirFolder);
                    }

                    // remove original data
                    if (uncompressed && autoremove) {
                        data_file.delete();
                    }
                }
            }
        }
        else
            System.err.println("Skipping " + fileName + " [already exists]");
    }
    
}
