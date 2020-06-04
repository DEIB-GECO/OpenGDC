package opengdc;

import opengdc.util.GDCData;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author fabio
 */
public class Main {
    
    private static HashSet<String> skip_diseases = new HashSet<>();
    private static HashSet<String> skip_datatypes = new HashSet<>();
    private static final String CMD = "download and convert";
    //private static final String CMD = "convert";
    private static final String ROOT = "/media/disk4/";
    //private static final String ROOT = "/FTP/ftp-root/";
    
    private static void initSkipDiseases() {
        skip_diseases = new HashSet<>();
    }
    
    private static void initSkipDataTypes() {
        skip_datatypes = new HashSet<>();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        /*String inputProgram = null;
        String inputDiseaseAbbreviation = null;
        if (args.length == 2) {
            inputProgram = args[0];
            inputDiseaseAbbreviation = args[1];
        }
        else {
            System.err.println("Usage example: java -jar app.jar 'program' 'disease'");
            System.exit(1);
        }*/
        
        //String inputProgram = "TCGA";
        //String inputDiseaseAbbreviation = "ACC";
        //String inputDisease = (inputProgram+"-"+inputDiseaseAbbreviation).toLowerCase();
        runMain();
    }
        
    public static void runMain() {
        initSkipDiseases();
        initSkipDataTypes();
        
        HashMap<String, HashMap<String, HashSet<String>>> gdcDataMap = GDCData.getBigGDCDataMap();
        for (String program: gdcDataMap.keySet()) {
            if (program.toLowerCase().trim().equals("tcga")) {
                for (String disease: gdcDataMap.get(program).keySet()) {
                    //if (disease.toLowerCase().equals(inputDisease.toLowerCase())) {
                        if (!skip_diseases.contains(disease.toLowerCase())) {
                        //if (todo_diseases.contains(disease.toLowerCase())) {
                            //HashSet<String> dataTypes = new HashSet<>();
                            //dataTypes.add("Clinical and Biospecimen Supplements");
                            for (String dataType: gdcDataMap.get(program).get(disease)) {
                            //for (String dataType: dataTypes) {
                                if (dataType.toLowerCase().contains("clinical"))
                                    dataType = "Clinical and Biospecimen Supplements";
                                else if (dataType.toLowerCase().contains("biospecimen"))
                                    dataType = null;
                                if (dataType != null) {
                                    if (!skip_datatypes.contains(dataType.toLowerCase())) {
                                        System.err.println(program + "\t" + disease + "\t" + dataType);
                                        try {
                                            if (CMD.trim().toLowerCase().contains("download")) {
                                                /** DOWNLOAD DATA **/
                                                String outDirStr = ROOT+"opengdc/original/"+program.toLowerCase()+"/"+disease.toLowerCase()+"/"+GDCData.getGDCData2FTPFolderName().get(dataType.toLowerCase())+"/";

                                                File outDir = new File(outDirStr);
                                                outDir.mkdirs();
                                                Settings.setOutputGDCFolder(outDirStr);

                                                String[] arr = new String[6];
                                                arr[0] = "download";            // Action name
                                                arr[1] = program;               // Program
                                                arr[2] = disease;               // Disease
                                                arr[3] = dataType;              // Data type
                                                arr[4] = "true";                // Auto-extract data
                                                arr[5] = "true";                // Auto-remove data

                                                Controller controller = new Controller();
                                                controller.execute(arr);
                                            }
                                            if (CMD.trim().toLowerCase().contains("convert")) {
                                                /** CONVERT DATA **/
                                                String inDirStr = ROOT+"opengdc/original/"+program.toLowerCase()+"/"+disease.toLowerCase()+"/"+GDCData.getGDCData2FTPFolderName().get(dataType.toLowerCase())+"/";
                                                String outDirStr = ROOT+"opengdc/bed/"+program.toLowerCase()+"/"+disease.toLowerCase()+"/"+GDCData.getGDCData2FTPFolderName().get(dataType.toLowerCase())+"/";

                                                File outDir = new File(outDirStr);
                                                outDir.mkdirs();
                                                Settings.setInputGDCFolder(inDirStr);
                                                Settings.setOutputConvertedFolder(outDirStr);

                                                String[] arr = new String[5];
                                                arr[0] = "convert";             // Action name
                                                arr[1] = program;               // Program
                                                arr[2] = disease;               // Disease
                                                arr[3] = dataType;              // Data type
                                                // Format
                                                if (dataType.toLowerCase().contains("clinical"))
                                                    arr[4] = "META";
                                                else
                                                    arr[4] = "BED";

                                                Controller controller = new Controller();
                                                controller.execute(arr);
                                            }
                                        }
                                        catch (Exception e) {
                                            System.err.println("An error has occurred for: "+disease);
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    //}
                }
            }
        }
                
    }
    
}
