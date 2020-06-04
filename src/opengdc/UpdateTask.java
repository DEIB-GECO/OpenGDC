/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opengdc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TimerTask;
import opengdc.action.Action;
import opengdc.action.ConvertDataAction;
import opengdc.action.DownloadDataAction;
import opengdc.util.FSUtils;
import opengdc.util.GDCData;
import opengdc.util.GDCQuery;
import opengdc.util.UpdateGDCData;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author fabio
 */
public class UpdateTask extends TimerTask {
    
    private static String ftp_root;
    private static String ftp_repo;
    private static String files_datetime;
    
    public UpdateTask(String ftp_root_arg, String ftp_repo_arg, String files_datetime_arg) {
        ftp_root = ftp_root_arg;
        ftp_repo = ftp_repo_arg;
	files_datetime = files_datetime_arg;
    }

    @Override
    public void run() {
        System.out.println("----------------------------------------");
        System.out.println("Timer task started at: " + new Date());
        //updateTask();
	Settings.setFilesDatetime(files_datetime)
	Main.runMain();
        System.out.println("Timer task finished at: " + new Date());
        System.out.println("----------------------------------------");
    }

    // deprecated
    // use Main class and set the CREATED_DATETIME variable under Settings
    private void updateTask() {
        Settings.setOpenGDCFTPRepoBase(ftp_repo);
        File tmp_download_dir = new File(Settings.getTmpDir() + "_download/");
        if (!tmp_download_dir.exists())
            tmp_download_dir.mkdirs();
        HashMap<String, HashMap<String, HashSet<String>>> GDCDataMap = GDCData.getBigGDCDataMap();
        HashMap<String, String> dataType2DirName = GDCData.getGDCData2FTPFolderName();
        for (String program: GDCDataMap.keySet()) {
            if (program.toLowerCase().equals("tcga")) {
                for (String tumor: GDCDataMap.get(program).keySet()) {
                    if (tumor.toLowerCase().equals("tcga-ucs")) {
                        /*************************************************/
                        ArrayList<String> dataTypes = new ArrayList<>(GDCDataMap.get(program).get(tumor));
                        // rename clinical and biospecimen supplements
                        if (dataTypes.contains("Clinical Supplement"))
                            dataTypes.remove("Clinical Supplement");
                        if (dataTypes.contains("Biospecimen Supplement"))
                            dataTypes.remove("Biospecimen Supplement");
                        // put clinical and biospecimen supplements at the end of the set
                        dataTypes.add("Clinical and Biospecimen Supplements");
                        /*************************************************/
                        for (String dataType: dataTypes) {
                            if (dataType.toLowerCase().equals("clinical and biospecimen supplements")) {
                                try {                        
                                    String original_local_data_dir = ftp_root + "original" + "/" + program.toLowerCase() + "/" + tumor.toLowerCase() + "/" + dataType2DirName.get(dataType.toLowerCase()) + "/"; 
                                    if (!(new File(original_local_data_dir)).exists())
                                        (new File(original_local_data_dir)).mkdirs();
                                    String converted_local_data_dir = ftp_root + "bed" + "/" + program.toLowerCase() + "/" + tumor.toLowerCase() + "/" + dataType2DirName.get(dataType.toLowerCase()) + "/"; 
                                    if (!(new File(converted_local_data_dir)).exists())
                                        (new File(converted_local_data_dir)).mkdirs();
                                    String updatetable_original_path = original_local_data_dir + Settings.getUpdateTableName();
                                    if (!(new File(updatetable_original_path)).exists())
                                        (new File(updatetable_original_path)).createNewFile();
                                    HashMap<String, HashMap<String, String>> updatetable_original = UpdateGDCData.loadUpdateTable_original(updatetable_original_path);
                                    String updatetable_converted_path = converted_local_data_dir + Settings.getUpdateTableName();
                                    if (!(new File(updatetable_converted_path)).exists())
                                        (new File(updatetable_converted_path)).createNewFile();
                                    HashMap<String, HashMap<String, String>> updatetable_converted = UpdateGDCData.loadUpdateTable_converted(updatetable_converted_path);

                                    ArrayList<String> subTypes = new ArrayList<>();
                                    if (dataType.equals("Clinical and Biospecimen Supplements")) {
                                        subTypes.add("Clinical Supplement");
                                        subTypes.add("Biospecimen Supplement");
                                    }
                                    else
                                        subTypes.add(dataType);

                                    for (String type: subTypes)
                                        downloadStep(tumor, type, original_local_data_dir, converted_local_data_dir, updatetable_original_path, updatetable_original, updatetable_converted_path, updatetable_converted, tmp_download_dir);

                                    if (tmp_download_dir.list().length > 0) {
                                        // convert data
                                        Action convertAction = new ConvertDataAction();
                                        String[] convert_params = new String[8];
                                        convert_params[0] = "convert";
                                        convert_params[1] = program;
                                        convert_params[2] = tumor;
                                        convert_params[3] = dataType;
                                        convert_params[4] = "bed";
                                        if (dataType.equals("Clinical and Biospecimen Supplements"))
                                            convert_params[4] = "meta";
                                        convert_params[5] = "false";
                                        convert_params[6] = "true";
                                        convert_params[7] = updatetable_converted_path;
                                        Settings.setInputGDCFolder(tmp_download_dir.getAbsolutePath());
                                        Settings.setOutputConvertedFolder(converted_local_data_dir);
                                        convertAction.execute(convert_params);
                                        // move downloaded files from the tmp dir to the original one
                                        for (File file : tmp_download_dir.listFiles()) {
                                            //tmp_download_dir.listFiles()[f].renameTo(new File( original_local_data_dir + tmp_download_dir.listFiles()[f].getName() ));
                                            //FileUtils.moveFileToDirectory(file, new File(original_local_data_dir), true);
                                            FileUtils.moveFile(file, new File( original_local_data_dir + file.getName() ));
                                        }
                                        // just to be sure that the tmp download dir will be empty before the next data type
                                        if (tmp_download_dir.list().length > 0)
                                            FSUtils.deleteDir(tmp_download_dir);
                                    }
                                }
                                catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
        FSUtils.deleteDir(tmp_download_dir);
    }    
    
    private void downloadStep(String tumor, String dataType, String original_local_data_dir, String converted_local_data_dir, 
                              String updatetable_original_path, HashMap<String, HashMap<String, String>> updatetable_original, 
                              String updatetable_converted_path, HashMap<String, HashMap<String, String>> updatetable_converted, 
                              File tmp_download_dir) throws Exception {
        String query_file_path = GDCQuery.query(tumor, dataType, 0);
        HashMap<String, HashMap<String, String>> dataMap = GDCQuery.extractInfo(query_file_path);
        boolean first_iter = true;
        for (String uuid: dataMap.keySet()) {
            if (!FSUtils.filePrefixExists(uuid, original_local_data_dir)) {
                String current_md5 = dataMap.get(uuid).get("md5sum");
                String current_updated_datetime = dataMap.get(uuid).get("updated_datetime");
                String current_file_name = dataMap.get(uuid).get("file_name");
                //String current_file_id = dataMap.get(uuid).get("file_id");
                String current_file_size = dataMap.get(uuid).get("file_size");
                if (dataType.equals("Clinical Supplement") || dataType.equals("Biospecimen Supplement") || dataType.equals("Masked Somatic Mutation")) {
                    if (first_iter) {
                        File originalDir = new File(original_local_data_dir);
                        FSUtils.deleteDir(originalDir);
                        originalDir.mkdirs();
                        (new File(updatetable_original_path)).createNewFile();
                        updatetable_original = new HashMap<>();
                        File convertedDir = new File(converted_local_data_dir);
                        FSUtils.deleteDir(convertedDir);
                        convertedDir.mkdirs();
                        (new File(updatetable_converted_path)).createNewFile();
                        updatetable_converted = new HashMap<>();
                    }
                    
                    // download new original file
                    SimpleDateFormat  format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
		    String file_downloadDate = format.format(new Date()).replaceAll("(.*)(\\d\\d)$", "$1:$2");  
                    DownloadDataAction.downloadSingleData(uuid, dataMap, tmp_download_dir.getAbsolutePath(), true, true);
                    // modify updatetable_original
                    updatetable_original.remove(uuid);
                    HashMap<String, String> updateInfo = new HashMap<>();
                    updateInfo.put("md5sum", current_md5);
                    updateInfo.put("updated_datetime", current_updated_datetime);
                    updateInfo.put("file_name", current_file_name);
                    updateInfo.put("file_id", uuid);
                    updateInfo.put("file_size", current_file_size);
                    updateInfo.put("downloaded_datetime", file_downloadDate);
                    updatetable_original.put(uuid, updateInfo);
                    if (first_iter) {
                        (new File(updatetable_original_path)).delete();
                        (new File(updatetable_original_path)).createNewFile();
                        first_iter = false;
                    }
                    for (String fileId: updatetable_original.keySet()) {
                        String fileRow = fileId + "\t" + updatetable_original.get(fileId).get("file_name") + "\t" + updatetable_original.get(fileId).get("file_size") + "\t" + updatetable_original.get(fileId).get("md5sum") + "\t" + updatetable_original.get(fileId).get("updated_datetime") + "\t" + updatetable_original.get(fileId).get("downloaded_datetime") + "\n";
                        Files.write((new File(updatetable_original_path)).toPath(), (fileRow).getBytes("UTF-8"), StandardOpenOption.APPEND);
                    }
                    // load updatetable again
                    updatetable_original = UpdateGDCData.loadUpdateTable_original(updatetable_original_path);
                }
                else {
                    String aliquot_uuid = getAliquotUUID(uuid, dataType);
                    if (FSUtils.filePrefixExists(aliquot_uuid, converted_local_data_dir)) {
                        String converted_file_uuid = updatetable_converted.get(aliquot_uuid).get("file_id");
                        FSUtils.deleteFilesWithPrefix(aliquot_uuid, converted_local_data_dir);
                        FSUtils.deleteFilesWithPrefix(converted_file_uuid, original_local_data_dir);
                        // download new original file
                        SimpleDateFormat  format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
			String file_downloadDate = format.format(new Date()).replaceAll("(.*)(\\d\\d)$", "$1:$2");  
                        DownloadDataAction.downloadSingleData(uuid, dataMap, tmp_download_dir.getAbsolutePath(), true, true);
                        // modify updatetable_original
                        updatetable_original.remove(uuid);
                        HashMap<String, String> updateInfo = new HashMap<>();
                        updateInfo.put("md5sum", current_md5);
                        updateInfo.put("updated_datetime", current_updated_datetime);
                        updateInfo.put("file_name", current_file_name);
                        updateInfo.put("file_id", uuid);
                        updateInfo.put("file_size", current_file_size);
                        updateInfo.put("downloaded_datetime", file_downloadDate);
                        updatetable_original.put(uuid, updateInfo);
                        (new File(updatetable_original_path)).delete();
                        (new File(updatetable_original_path)).createNewFile();
                        for (String fileId: updatetable_original.keySet()) {
                            String fileRow = fileId + "\t" + updatetable_original.get(fileId).get("file_name") + "\t" + updatetable_original.get(fileId).get("file_size") + "\t" + updatetable_original.get(fileId).get("md5sum") + "\t" + updatetable_original.get(fileId).get("updated_datetime") + "\t" + updatetable_original.get(fileId).get("downloaded_datetime") + "\n";
                            Files.write((new File(updatetable_original_path)).toPath(), (fileRow).getBytes("UTF-8"), StandardOpenOption.APPEND);
                        }
                        // load updatetable again
                        updatetable_original = UpdateGDCData.loadUpdateTable_original(updatetable_original_path);
                        // modify updatetable_converted
                        updatetable_converted.remove(aliquot_uuid);
                    }
                    else {
                        // download original file
			SimpleDateFormat  format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
			String file_downloadDate = format.format(new Date()).replaceAll("(.*)(\\d\\d)$", "$1:$2");
                        DownloadDataAction.downloadSingleData(uuid, dataMap, tmp_download_dir.getAbsolutePath(), true, true);
                        // modify updatetable
                        HashMap<String, String> updateInfo = new HashMap<>();
                        updateInfo.put("md5sum", current_md5);
                        updateInfo.put("updated_datetime", current_updated_datetime);
                        updateInfo.put("file_name", current_file_name);
                        updateInfo.put("file_id", uuid);
                        updateInfo.put("file_size", current_file_size);
                        updateInfo.put("downloaded_datetime", file_downloadDate);
                        updatetable_original.put(uuid, updateInfo);
                        String fileRow = uuid + "\t" + updatetable_original.get(uuid).get("file_name") + "\t" + updatetable_original.get(uuid).get("file_size") + "\t" + updatetable_original.get(uuid).get("md5sum") + "\t" + updatetable_original.get(uuid).get("updated_datetime") + "\t" + updatetable_original.get(uuid).get("downloaded_datetime") + "\n";
                        Files.write((new File(updatetable_original_path)).toPath(), (fileRow).getBytes("UTF-8"), StandardOpenOption.APPEND);
                    }
                }
            }
        }
        /*if (!dataType.equals("Clinical Supplement") && !dataType.equals("Biospecimen Supplement") && !dataType.equals("Masked Somatic Mutation")) {
            // remove old and no more maintainable files
            HashMap<String, HashMap<String, String>> new_updatetable_original = new HashMap<>();
            for (String file_name: updatetable_original.keySet()) {
                String file_uuid = updatetable_original.get(file_name).get("file_id");
                if (dataMap.containsKey(file_uuid))
                    new_updatetable_original.put(file_name, updatetable_original.get(file_name));
            }
            if (new_updatetable_original.size() != updatetable_original.size()) {
                (new File(updatetable_original_path)).delete();
                (new File(updatetable_original_path)).createNewFile();
                for (String file_name: new_updatetable_original.keySet()) {
                    String fileRow = new_updatetable_original.get(file_name).get("file_name") + "\t" + new_updatetable_original.get(file_name).get("file_size") + "\t" + new_updatetable_original.get(file_name).get("md5sum") + "\t" + new_updatetable_original.get(file_name).get("updated_datetime") + "\t" + new_updatetable_original.get(file_name).get("downloaded_datetime") + "\n";
                    Files.write((new File(updatetable_original_path)).toPath(), (fileRow).getBytes("UTF-8"), StandardOpenOption.APPEND);
                }
            }
        }*/
    }

    private String getAliquotUUID(String file_uuid, String dataType) {
        HashSet<String> attributes = new HashSet<>();
        String aliquot_id_path = "cases.samples.portions.analytes.aliquots.aliquot_id";
        attributes.add(aliquot_id_path);
        HashSet<String> dataTypes = new HashSet<>();
        dataTypes.add(dataType);
        HashMap<String, ArrayList<Object>> file_info = GDCQuery.retrieveExpInfoFromAttribute("files", "files.file_id", file_uuid, dataTypes, attributes, 0, 0, null).get(0);
        String aliquot_uuid = "";
        if (file_info != null) {
            if (file_info.containsKey("cases.samples.portions.analytes.aliquots.aliquot_id")) {
                for (String k: file_info.keySet()) {
                    for (Object obj: file_info.get(k)) {
                        HashMap<String, Object> map = (HashMap<String, Object>)obj;
                        for (String kmap: map.keySet()) {
                            try {
                                if (kmap.toLowerCase().equals("cases.samples.portions.analytes.aliquots.aliquot_id"))
                                    aliquot_uuid = String.valueOf(map.get(kmap));
                            }
                            catch (Exception e) { }
                        }
                    }
                }
            }
        }
        return aliquot_uuid;
    }
}
