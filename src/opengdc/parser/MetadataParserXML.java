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
import opengdc.util.XMLNode;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author fabio
 */
public class MetadataParserXML extends BioParser {
    
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
        
        File[] files = (new File(inPath)).listFiles();
        int progress_counter = 1;
        for (File f: files) {
            // remove all elements in aliquotNodes from previous iterations
            MetadataHandler.emptyAliquotNodes();
            if (f.isFile()) {
                String extension = FSUtils.getFileExtension(f);
                if (getAcceptedInputFileFormats().contains(extension) && !getSkipFiles().contains(f.getName().toLowerCase())) {
                    String file_uuid = f.getName().split("_")[0];
                    System.err.println("Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName());
                    GUI.appendLog(this.getLogger(), "Processing entry " + progress_counter + "/" + acceptedFiles + ": " + f.getName() + "\n");
                    
                    HashMap<String, Object> metadata_from_xml = MetadataHandler.getXMLMap(f.getAbsolutePath());
                    if (f.getName().toLowerCase().contains("clinical")) {                        
                        HashMap<String, String> clinical_metadata_map = MetadataHandler.getDataMap(metadata_from_xml, null);
                        String patient_uuid = MetadataHandler.findKey(clinical_metadata_map, "endsWith", "bcr_patient_uuid");
                        clinicalBigMap.put(patient_uuid, clinical_metadata_map);
                    }
                    else if (f.getName().toLowerCase().contains("biospecimen")) {                        
                        XMLNode root = new XMLNode();
                        root.setRoot();
                        root = MetadataHandler.convertMapToIndexedTree(metadata_from_xml, root);
                        MetadataHandler.searchForAliquots(root);
                        HashMap<String, String> admin_metadata_map = MetadataHandler.extractAdminInfo(metadata_from_xml);
                        
                        HashMap<String, HashMap<String, String>> aliquot2attributes = new HashMap<>();
                        for (XMLNode aliquot: MetadataHandler.getAliquotNodes()) {
                            String aliquot_uuid = MetadataHandler.findKey(aliquot.getAttributes(), "endsWith", "bcr_aliquot_uuid");
                            if (!aliquot_uuid.equals("")) {
                                HashMap<String, String> aliquotMeta = aliquot.getAttributes();
                                HashMap<String, String> parentMeta = MetadataHandler.extractParentMetadata(aliquot.getParent(), new HashMap<>());
                                aliquotMeta.putAll(parentMeta);
                                aliquotMeta.putAll(admin_metadata_map);
                                aliquot2attributes.put(aliquot_uuid, aliquotMeta);
                            }
                        }
                        
                        if (!aliquot2attributes.isEmpty()) {
                            for (String aliquot_uuid: aliquot2attributes.keySet()) {
                                biospecimenBigMap.put(aliquot_uuid, aliquot2attributes.get(aliquot_uuid));
                                aliquotUUID2fileUUID.put(aliquot_uuid, file_uuid);
                            }
                        }
                    }
                }
            }
            progress_counter++;
        }
        
        ArrayList<String> skippedAliquots = convertProcedure(program, disease, dataType, outPath, clinicalBigMap, biospecimenBigMap, new ArrayList<>(), aliquotUUID2fileUUID);
        if (!skippedAliquots.isEmpty())
            convertProcedure(program, disease, dataType, outPath, clinicalBigMap, biospecimenBigMap, skippedAliquots, aliquotUUID2fileUUID);
        
        return 0;
    }
    
    private ArrayList<String> convertProcedure(String program, String disease, String dataType, String outPath, HashMap<String, HashMap<String, String>> clinicalBigMap, HashMap<String, HashMap<String, String>> biospecimenBigMap, ArrayList<String> skippedAliquots, HashMap<String, String> aliquotUUID2fileUUID) {
        ArrayList<String> currentSkippedAliquots = new ArrayList<>();
        // merge clinical and biospecimen info (plus additional metadata)
        if (!biospecimenBigMap.isEmpty()) {
            HashSet<String> dataTypes = new HashSet<>();
            dataTypes.add("Gene Expression Quantification");
            dataTypes.add("Copy Number Segment");
            dataTypes.add("Masked Copy Number Segment");
            dataTypes.add("Methylation Beta Value");
            dataTypes.add("Isoform Expression Quantification");
            dataTypes.add("miRNA Expression Quantification");
            dataTypes.add("Masked Somatic Mutation");
            
            HashMap<String, HashMap<String, Boolean>> additional_attributes_files = MetadataHandler.getAdditionalAttributes("files");
            HashMap<String, HashMap<String, Boolean>> additional_attributes_cases = MetadataHandler.getAdditionalAttributes("cases");
            
            for (String aliquot_uuid: biospecimenBigMap.keySet()) {
                try {
                    if (skippedAliquots.isEmpty() || skippedAliquots.contains(aliquot_uuid)) {
                        // print biospecimen info
                        String patient_uuid = "";
                        for (String attribute: biospecimenBigMap.get(aliquot_uuid).keySet()) {
                            if (MetadataHandler.getAttributeFromKey(attribute).trim().toLowerCase().equals("shared:bcr_patient_uuid")) {
                                patient_uuid = biospecimenBigMap.get(aliquot_uuid).get(attribute);
                                break;
                            }
                        }
                        HashMap<String, String> biospecimen_map = new HashMap<>();
                        for (String attr: biospecimenBigMap.get(aliquot_uuid).keySet())
                            biospecimen_map.put(MetadataHandler.getAttributeFromKey(attr).replaceAll(":","__"), biospecimenBigMap.get(aliquot_uuid).get(attr));
                        ArrayList<String> biospecimen_sorted = new ArrayList<>(biospecimen_map.keySet());
                        Collections.sort(biospecimen_sorted);

                        // print clinical info
                        ArrayList<String> clinical_sorted = new ArrayList<>();
                        HashMap<String, String> clinical_map = new HashMap<>();
                        if (!patient_uuid.equals("")) {
                            if (clinicalBigMap.containsKey(patient_uuid)) {
                                for (String attr: clinicalBigMap.get(patient_uuid).keySet())
                                    clinical_map.put(MetadataHandler.getAttributeFromKey(attr).replaceAll(":","__"), clinicalBigMap.get(patient_uuid).get(attr));
                                clinical_sorted = new ArrayList<>(clinical_map.keySet());
                                Collections.sort(clinical_sorted);
                            }
                        }

                        // generate manually curated metadata
                        if (!additional_attributes_files.isEmpty() || !additional_attributes_cases.isEmpty()) {
                            ArrayList<String> additional_attributes_sorted = new ArrayList<>(additional_attributes_files.keySet());
                            Collections.sort(additional_attributes_sorted);
                            for (String metakey: additional_attributes_sorted) {
                                HashMap<String, Boolean> additional_attributes_files_tmp = additional_attributes_files.get(metakey);
                                HashMap<String, Boolean> additional_attributes_cases_tmp = additional_attributes_cases.get(metakey);
                                //additional_attributes_files_tmp.remove("cases.samples.sample_id");
                                //additional_attributes_cases_tmp.remove("samples.sample_id");
                                
                                //ArrayList<HashMap<String, ArrayList<Object>>> files_info = GDCQuery.retrieveExpInfoFromAttribute("files", "cases.samples.portions.analytes.aliquots.aliquot_id", aliquot_uuid.toLowerCase(), new HashSet<>(additional_attributes_files.get(metakey).keySet()), 0, 0, null);
                                ArrayList<HashMap<String, ArrayList<Object>>> files_info = GDCQuery.retrieveExpInfoFromAttribute("files", "cases.samples.portions.analytes.aliquots.aliquot_id", aliquot_uuid.toLowerCase(), dataTypes, new HashSet<>(additional_attributes_files.get(metakey).keySet()), 0, 0, null);
                                //ArrayList<HashMap<String, String>> aggregated_files_info = MetadataHandler.aggregateSameDataTypeInfo(files_info, MetadataHandler.getAggregatedAdditionalAttributes());
                                ArrayList<HashMap<String, String>> aggregated_files_info = MetadataHandler.aggregateSameDataTypeInfo(files_info, new ArrayList<>(MetadataHandler.getAdditionalAttributes("files").get("gdc").keySet()));

                                boolean use_files_endpoint = true;
                                if (aggregated_files_info.isEmpty()) {
                                    use_files_endpoint = false;
                                    HashSet<String> additional_attributes_tmp = new HashSet<>(additional_attributes_cases_tmp.keySet());
                                    files_info = GDCQuery.retrieveExpInfoFromAttribute("cases", "samples.portions.analytes.aliquots.aliquot_id", aliquot_uuid, dataTypes, additional_attributes_tmp, 0, 0, null);
                                    if (!files_info.isEmpty()) {
                                        HashMap<String, String> files_info_res = new HashMap<>();
                                        for (HashMap<String, ArrayList<Object>> file_info: files_info) {
                                            for (String k: file_info.keySet()) {
                                                for (Object obj: file_info.get(k)) {
                                                    HashMap<String, Object> map = (HashMap<String, Object>)obj;
                                                    for (String kmap: map.keySet()) {
                                                        String add_attr_curr = "";
                                                        for (String add_attr: additional_attributes_tmp) {
                                                            //String[] add_attr_split = add_attr.split("\\.");
                                                            //String last_val = add_attr_split[add_attr_split.length-1];
                                                            //if (last_val.toLowerCase().equals(kmap.toLowerCase())) {
                                                            if (add_attr.toLowerCase().equals(kmap.toLowerCase())) {
                                                                //files_info_res.put(last_val, String.valueOf(map.get(kmap)));
                                                                files_info_res.put(add_attr, String.valueOf(map.get(kmap)));
                                                                add_attr_curr = add_attr;
                                                                break;
                                                            }
                                                        }
                                                        if (!add_attr_curr.trim().equals(""))
                                                            additional_attributes_tmp.remove(add_attr_curr);
                                                    }
                                                }
                                            }
                                        }
                                        aggregated_files_info = new ArrayList<>();
                                        aggregated_files_info.add(files_info_res);
                                    }
                                }

                                for (HashMap<String, String> file_info: aggregated_files_info) {
                                    if (file_info != null) {
                                        // handle missing required attributes
                                        HashSet<String> missing_required_attributes = new HashSet<>();
                                        HashMap<String, String> gdc_map = new HashMap<>();
                                        HashMap<String, Boolean> attribute2required;
                                        HashMap<String, Boolean> additional_attributes_tmp;

                                        if (!use_files_endpoint) {
                                            attribute2required = additional_attributes_cases.get(metakey);
                                            additional_attributes_tmp = additional_attributes_cases_tmp;
                                        }
                                        else { 
                                            attribute2required = additional_attributes_files.get(metakey);
                                            additional_attributes_tmp = additional_attributes_files_tmp;
                                        }
                                        
                                        ArrayList<String> file_info_sorted = new ArrayList<>(file_info.keySet());
                                        Collections.sort(file_info_sorted);
                                        
                                        ArrayList<String> manually_without_cases = MetadataHandler.getManuallyCuratedAttributesWithNoCases();

                                        //start warning missing attribute
                                        for (String attribute: additional_attributes_tmp.keySet()) {
                                            String attribute_parsed;
                                            String attribute_tmp = "";
                                            for (String attr: manually_without_cases) {
                                                if (attr.equals(attribute) && !use_files_endpoint) {
                                                    attribute_tmp = attr;
                                                    break;
                                                }
                                            }
                                            
                                            if (!attribute_tmp.equals(""))
                                                attribute_parsed = metakey + "__cases__" + attribute_tmp.replaceAll("\\.", "__");
                                            else
                                                attribute_parsed = metakey + "__" + attribute.replaceAll("\\.", "__");
                                            //String[] attribute_split = attribute.split("\\.");
                                            //if (additional_attributes_tmp.containsKey(attribute) && !file_info.containsKey(attribute_split[attribute_split.length-1]) && attribute2required.containsKey(attribute)) {
                                            if (additional_attributes_tmp.containsKey(attribute) && !file_info.containsKey(attribute) && attribute2required.containsKey(attribute)) {
                                                if (attribute2required.get(attribute))
                                                    missing_required_attributes.add(attribute_parsed);
                                            }
                                        } //end warning missing attribute
                                        
                                        for (String attribute: additional_attributes_tmp.keySet()) {
                                        //for (String attribute: file_info_sorted) {
                                            //String attribute_parsed = FSUtils.stringToValidJavaIdentifier(metakey + "__" + attribute.replaceAll("\\.", "__"));
                                            //String attribute_spitted = attribute.split("\\.")[attribute.split("\\.").length-1];
                                            //if (file_info_sorted.contains(attribute_spitted)) {
                                            if (file_info_sorted.contains(attribute)) {
                                                String attribute_parsed;
                                                String attribute_tmp = "";
                                                for (String attr: manually_without_cases) {
                                                    if (attr.equals(attribute) && !use_files_endpoint) {
                                                        attribute_tmp = attr;
                                                        break;
                                                    }
                                                }

                                                if (!attribute_tmp.equals(""))
                                                    attribute_parsed = metakey + "__cases__" + attribute_tmp.replaceAll("\\.", "__");
                                                else
                                                    attribute_parsed = metakey + "__" + attribute.replaceAll("\\.", "__");
                                                /*************************************************************/
                                                /** patch for the attribute 'manually_curated__data_format' **/
                                                if (attribute_parsed.trim().toLowerCase().equals("manually_curated__data_format"))
                                                    attribute_parsed = "gdc__source_data_format";
                                                /*************************************************************/
                                                //String value_parsed = checkForNAs(file_info.get(attribute_spitted));
                                                String value_parsed = checkForNAs(file_info.get(attribute));
                                                if (!value_parsed.trim().equals(""))
                                                    gdc_map.put(attribute_parsed, value_parsed);
                                                /*else {
                                                    for (String attr: additional_attributes_files_tmp.keySet()) {
                                                        if (attr.toLowerCase().equals(attribute.toLowerCase())) {
                                                            if (additional_attributes_files_tmp.get(attr)) // if attribute is required
                                                                missing_required_attributes.add(attribute_parsed);
                                                        }
                                                    }
                                                }*/
                                            }
                                        }

                                        // generate additional manually curated metadata
                                        String manually_curated_data_type = "";
                                        for (String mcattr: gdc_map.keySet()) {
                                            if (mcattr.toLowerCase().contains("gdc__data_type")) {
                                                manually_curated_data_type = gdc_map.get(mcattr);
                                                break;
                                            }
                                        }

                                        // create a suffix to append to the aliquot id
                                        String suffix_id = this.getOpenGDCSuffix(manually_curated_data_type, false);
                                        HashMap<String, String> manually_curated = new HashMap<>();
                                        HashMap<String, HashMap<String, Object>> additional_manually_curated = MetadataHandler.getAdditionalManuallyCuratedAttributes(program, disease, dataType, this.getFormat(), aliquot_uuid, "", biospecimenBigMap.get(aliquot_uuid), clinicalBigMap.get(patient_uuid), gdc_map, suffix_id, manually_curated_data_type);
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
                                            HashMap<String, String> bigMap = new HashMap<>();

                                            // biospecimen
                                            for (String attribute: biospecimen_sorted) {
                                                String[] attribute_split = attribute.split("__");
                                                String attribute_parsed = "";
                                                for (String attr: attribute_split)
                                                    attribute_parsed = attribute_parsed + FSUtils.stringToValidJavaIdentifier(attr) + "__";
                                                attribute_parsed = "biospecimen__" + attribute_parsed.substring(0, attribute_parsed.length()-2);
                                                String value_parsed = checkForNAs(biospecimen_map.get(attribute));
                                                if (!value_parsed.trim().equals("")) {
                                                    //out.println(attribute_parsed + "\t" + value_parsed);
                                                    bigMap.put(attribute_parsed, value_parsed);
                                                }
                                            }

                                            // clinical
                                            for (String attribute: clinical_sorted) {
                                                String[] attribute_split = attribute.split("__");
                                                String attribute_parsed = "";
                                                for (String attr: attribute_split)
                                                    attribute_parsed = attribute_parsed + FSUtils.stringToValidJavaIdentifier(attr) + "__";
                                                attribute_parsed = "clinical__" + attribute_parsed.substring(0, attribute_parsed.length()-2);
                                                String value_parsed = checkForNAs(clinical_map.get(attribute));
                                                if (!value_parsed.trim().equals("")) {
                                                    //out.println(attribute_parsed + "\t" + value_parsed);
                                                    bigMap.put(attribute_parsed, value_parsed);
                                                }
                                            }

                                            // gdc attributes
                                            ArrayList<String> gdc_attributes_sorted = new ArrayList<>(gdc_map.keySet());
                                            Collections.sort(gdc_attributes_sorted);
                                            for (String attr: gdc_attributes_sorted) {
                                                //out.println(attr + "\t" + manually_curated.get(attr));
                                                bigMap.put(attr, gdc_map.get(attr));
                                            }

                                            // generate audit_warning
                                            if (!missing_required_attributes.isEmpty()) {
                                                String missed_attributes_list = "";
                                                for (String ma: missing_required_attributes)
                                                    missed_attributes_list += ma+", ";
                                                manually_curated.put("manually_curated__audit_warning", "["+missed_attributes_list.substring(0, missed_attributes_list.length()-2)+"]");
                                            }
                                            
                                            //if (!manually_curated_data_type.equals("")) {
                                            // sort and print manually_curated attributes
                                            ArrayList<String> manually_curated_attributes_sorted = new ArrayList<>(manually_curated.keySet());
                                            Collections.sort(manually_curated_attributes_sorted);
                                            for (String attr: manually_curated_attributes_sorted) {
                                                //out.println(attr + "\t" + manually_curated.get(attr));
                                                bigMap.put(attr, manually_curated.get(attr));
                                            }
                                            //}
                                            // remove redundant attributes
                                            HashMap<String, HashMap<String, String>> redundant_map = MetadataHandler.detectRedundantMetadata(bigMap);
                                            HashMap<String, String> final_map = MetadataHandler.filterOutRedundantMetadata(redundant_map, "tcga");
                                            final_map = MetadataHandler.renameAttributes(final_map);
                                            ArrayList<String> final_map_attributes_sorted = new ArrayList<>(final_map.keySet());
                                            Collections.sort(final_map_attributes_sorted);
                                            
                                            // print all
                                            FileOutputStream fos = new FileOutputStream(out_file);
                                            PrintStream out = new PrintStream(fos);
                                            for (String attr: final_map_attributes_sorted)
                                                out.println(attr + "\t" + final_map.get(attr));
                                            out.close();
                                            fos.close();
                                        }
                                    }
                                }
                            }
                        }
                        
                        // check for skipped aliquots
                        boolean aliquotNotFound = true;
                        for (File aliquotFile: (new File(outPath)).listFiles()) {
                            if (aliquotFile.getName().startsWith(aliquot_uuid)) {
                                aliquotNotFound = false;
                                
                                if (this.isUpdateTableEnabled()) {
                                	SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ");
                					String file_convertedDate = format.format(new Date()).replaceAll("(.*)(\\d\\d)$", "$1:$2");                          				
                                    String updatetable_row = aliquot_uuid + "\t" + aliquotUUID2fileUUID.get(aliquot_uuid) + "\t" + file_convertedDate + "\t" + FSUtils.getFileChecksum(aliquotFile) + "\t" + String.valueOf(FileUtils.sizeOf(aliquotFile) + "\n");
                                    Files.write((new File(this.getUpdateTablePath())).toPath(), (updatetable_row).getBytes("UTF-8"), StandardOpenOption.APPEND);
                                }
                                break;
                            }
                        }
                        if (aliquotNotFound)
                            currentSkippedAliquots.add(aliquot_uuid);                            
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return currentSkippedAliquots;
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
        this.acceptedInputFileFormats.add(".xml");
    }
    
}
