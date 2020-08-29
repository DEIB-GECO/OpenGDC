package opengdc.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import opengdc.GUI;
import opengdc.util.FSUtils;
import opengdc.util.GDCQuery;
import opengdc.util.MetadataHandler;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author fabio
 */
public class MetadataParserTSV extends BioParser {
    
    /** FM **/
    
    @Override
    public int convert(String program, String disease, String dataType, String inPath, String outPath) {
        int acceptedFiles = FSUtils.acceptedFilesInFolder(inPath, getAcceptedInputFileFormats());
        System.err.println("Data Amount: " + acceptedFiles + " files" + "\n\n");
        GUI.appendLog(this.getLogger(), "Data Amount: " + acceptedFiles + " files" + "\n\n");
        
        if (acceptedFiles == 0)
            return 1;
        
        if (this.isRecoveryEnabled()) {
            // if the output folder is not empty, delete the most recent file
            File folder = new File(outPath);
            File[] files_out = folder.listFiles();
            if (files_out.length != 0) {
               File last_modified =files_out[0];
               long time = 0;
               for (File file : files_out) {
                  if (file.getName().endsWith(this.getFormat()) && !getSkipFiles().contains(file.getName().toLowerCase())) {
                     if (file.lastModified() > time) {  
                        time = file.lastModified();
                        last_modified = file;
                     }
                  }
               }
               System.err.println("File deleted: " + last_modified.getName());
               last_modified.delete();
            }
        }
        
        HashMap<String, HashMap<String, String>> clinicalBigMap = new HashMap<>();
        HashMap<String, HashMap<String, String>> biospecimenBigMap = new HashMap<>();
        HashMap<String, String> aliquotUUID2fileUUID = new HashMap<>();
        
        HashSet<String> dataTypes = new HashSet<>();
        dataTypes.add("Gene Expression Quantification");
        dataTypes.add("Copy Number Segment");
        dataTypes.add("Masked Copy Number Segment");
        dataTypes.add("Methylation Beta Value");
        dataTypes.add("Isoform Expression Quantification");
        dataTypes.add("miRNA Expression Quantification");
        dataTypes.add("Masked Somatic Mutation");
        
        File[] files = (new File(inPath)).listFiles();
        int progress_counter = 1;
        for (File f: files) {
            if (f.isFile()) {
                String extension = FSUtils.getFileExtension(f);
                if (getAcceptedInputFileFormats().contains(extension) && !getSkipFiles().contains(f.getName().toLowerCase())) {
                    String file_uuid = f.getName().split("_")[0];
                    System.err.println("Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName());
                    GUI.appendLog(this.getLogger(), "Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName() + "\n");
                    
                    
                    if (f.getName().toLowerCase().contains("clinical")) {                        
                        HashMap<String, HashMap<String, String>> metadata_from_tsv = MetadataHandler.getTSVMap(f.getAbsolutePath(), "case_id");
                        clinicalBigMap.putAll(metadata_from_tsv);
                    }
                    else if (f.getName().toLowerCase().contains("biospecimen")) {                        
                        HashMap<String, HashMap<String, String>> metadata_from_tsv = MetadataHandler.getTSVMap(f.getAbsolutePath(), "aliquot_id");
                        biospecimenBigMap.putAll(metadata_from_tsv);
                        for (String aliquot_uuid: metadata_from_tsv.keySet())
                            aliquotUUID2fileUUID.put(aliquot_uuid, file_uuid);
                    }
                }
            }
            progress_counter++;
        }
        
        if (!biospecimenBigMap.isEmpty()) {
            HashMap<String, HashMap<String, Boolean>> additional_attributes = MetadataHandler.getAdditionalAttributes("files");
            for (String aliquot_uuid: biospecimenBigMap.keySet()) {
                try {
                    String patient_uuid = biospecimenBigMap.get(aliquot_uuid).get("case_id");

                    // retrieve biospecimen
                    ArrayList<String> biospecimen_sorted = new ArrayList<>(biospecimenBigMap.get(aliquot_uuid).keySet());
                    Collections.sort(biospecimen_sorted);

                    // retrieve clinical
                    ArrayList<String> clinical_sorted = new ArrayList<>(clinicalBigMap.get(patient_uuid).keySet());
                    Collections.sort(clinical_sorted);

                    // generate manually curated metadata
                    if (!additional_attributes.isEmpty()) {
                        ArrayList<String> additional_attributes_sorted = new ArrayList<>(additional_attributes.keySet());
                        Collections.sort(additional_attributes_sorted);
                        for (String metakey: additional_attributes_sorted) {
                            ArrayList<HashMap<String, ArrayList<Object>>> files_info = GDCQuery.retrieveExpInfoFromAttribute("files", "cases.samples.portions.analytes.aliquots.aliquot_id", aliquot_uuid.toLowerCase(), dataTypes, new HashSet<>(additional_attributes.get(metakey).keySet()), 0, 0, null);
                            ArrayList<HashMap<String, String>> aggregated_files_info = MetadataHandler.aggregateSameDataTypeInfo(files_info, MetadataHandler.getAggregatedAdditionalAttributes());

                            for (HashMap<String, String> file_info: aggregated_files_info) {
                                if (file_info != null) {
                                    // handle missing required attributes
                                    HashSet<String> missing_required_attributes = new HashSet<>();
                                    HashMap<String, String> manually_curated = new HashMap<>();
                                    HashMap<String, Boolean> attribute2required = additional_attributes.get(metakey);
                                    ArrayList<String> file_info_sorted = new ArrayList<>(file_info.keySet());
                                    Collections.sort(file_info_sorted);
                                    for (String attribute: file_info_sorted) {
                                        //String attribute_parsed = FSUtils.stringToValidJavaIdentifier(metakey + "__" + attribute.replaceAll("\\.", "__"));
                                        String attribute_parsed = metakey + "__" + attribute.replaceAll("\\.", "__");
                                        /*************************************************************/
                                        /** patch for the attribute 'manually_curated__data_format' **/
                                        if (attribute_parsed.trim().toLowerCase().equals("manually_curated__data_format"))
                                            attribute_parsed = "manually_curated__source_data_format";
                                        /*************************************************************/
                                        String value_parsed = this.checkForNAs(file_info.get(attribute));
                                        if (!value_parsed.trim().equals(""))
                                            manually_curated.put(attribute_parsed, value_parsed);
                                        else {
                                            for (String attr: attribute2required.keySet()) {
                                                if (attr.toLowerCase().equals(attribute.toLowerCase())) {
                                                    if (attribute2required.get(attr)) // if attribute is required
                                                        missing_required_attributes.add(attribute_parsed);
                                                }
                                            }
                                        }
                                    }

                                    // generate additional manually curated metadata
                                    String manually_curated_data_type = "";
                                    for (String mcattr: manually_curated.keySet()) {
                                        if (mcattr.toLowerCase().contains("data_type")) {
                                            manually_curated_data_type = manually_curated.get(mcattr);
                                            break;
                                        }
                                    }
                                    // create a suffix to append to the aliquot id
                                    String suffix_id = this.getOpenGDCSuffix(manually_curated_data_type, false);

                                    HashMap<String, HashMap<String, Object>> additional_manually_curated = MetadataHandler.getAdditionalManuallyCuratedAttributes(program, disease, dataType, this.getFormat(), aliquot_uuid, "", biospecimenBigMap.get(aliquot_uuid), clinicalBigMap.get(patient_uuid), manually_curated, suffix_id, manually_curated_data_type);
                                    if (!additional_manually_curated.isEmpty()) {
                                        for (String attr: additional_manually_curated.keySet()) {
                                            //String attribute_parsed = FSUtils.stringToValidJavaIdentifier(attr);
                                            String attribute_parsed = attr;
                                            HashMap<String, Object> values = additional_manually_curated.get(attr);
                                            if (!values.isEmpty()) {
                                                String value_parsed = checkForNAs((String)additional_manually_curated.get(attr).get("value"));
                                                if (!value_parsed.trim().equals(""))
                                                    manually_curated.put(attribute_parsed, value_parsed);
                                                else {
                                                    if ((Boolean)additional_manually_curated.get(attr).get("required")) // if attribute is required
                                                        missing_required_attributes.add(attr);
                                                }
                                            }
                                        }
                                    }

                                    // create file if it does not exist
                                    File out_file = new File(outPath + aliquot_uuid.toLowerCase() + "-" + suffix_id + "." + this.getFormat());
                                    if (!out_file.exists()) {                                    
                                        FileOutputStream fos = new FileOutputStream(out_file);
                                        PrintStream out = new PrintStream(fos);

                                        // biospecimen
                                        for (String attribute: biospecimen_sorted) {
                                            String attribute_parsed = FSUtils.stringToValidJavaIdentifier("biospecimen__" + attribute.replaceAll("\\.", "__"));
                                            String value_parsed = checkForNAs(biospecimenBigMap.get(aliquot_uuid).get(attribute));
                                            if (!value_parsed.trim().equals(""))
                                                out.println(attribute_parsed + "\t" + value_parsed);
                                        }

                                        // clinical
                                        for (String attribute: clinical_sorted) {
                                            String attribute_parsed = FSUtils.stringToValidJavaIdentifier("clinical__" + attribute.replaceAll("\\.", "__"));
                                            String value_parsed = checkForNAs(clinicalBigMap.get(patient_uuid).get(attribute));
                                            if (!value_parsed.trim().equals(""))
                                                out.println(attribute_parsed + "\t" + value_parsed);
                                        }

                                        // generate audit_warning
                                        if (!missing_required_attributes.isEmpty()) {
                                            String missed_attributes_list = "";
                                            for (String ma: missing_required_attributes)
                                                missed_attributes_list += ma+", ";
                                            manually_curated.put("manually_curated__audit_warning", "["+missed_attributes_list.substring(0, missed_attributes_list.length()-2)+"]");
                                        }

                                        if (!manually_curated_data_type.equals("")) {
                                            // sort and print manually_curated attributes
                                            ArrayList<String> manually_curated_attributes_sorted = new ArrayList<>(manually_curated.keySet());
                                            Collections.sort(manually_curated_attributes_sorted);
                                            for (String attr: manually_curated_attributes_sorted)
                                                out.println(attr + "\t" + manually_curated.get(attr));
                                        }

                                        out.close();
                                        fos.close();
                                        
                                        if (this.isUpdateTableEnabled()) {
                                        	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
                        					String file_convertedDate = format.format(new Date()).replaceAll("(.*)(\\d\\d)$", "$1:$2"); 
                                            String updatetable_row = aliquot_uuid + "\t" + aliquotUUID2fileUUID.get(aliquot_uuid) + "\t" + file_convertedDate + "\t" + FSUtils.getFileChecksum(out_file) + "\t" + String.valueOf(FileUtils.sizeOf(out_file) + "\n");
                                            Files.write((new File(this.getUpdateTablePath())).toPath(), (updatetable_row).getBytes("UTF-8"), StandardOpenOption.APPEND);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }           
                        
        }
        
        return 0;
    }    

    @Override
    public String[] getHeader() {
        return null;
    }

    @Override
    public String[] getAttributesType() {
        return null;
    }

    @Override
    public void initAcceptedInputFileFormats() {
        this.acceptedInputFileFormats = new HashSet<>();
        this.acceptedInputFileFormats.add(".tsv");
    }
    
}
