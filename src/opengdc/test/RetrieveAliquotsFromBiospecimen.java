package opengdc.test;

import java.util.HashMap;
import opengdc.util.MetadataHandler;
import opengdc.util.XMLNode;

/**
 *
 * @author fabio
 */
public class RetrieveAliquotsFromBiospecimen {
    
    public static void main(String[] args) {
        // TCGA
        String biospecimen_absolute_path = "/Users/fabio/Downloads/e7024cbe-a60d-429c-b658-bcb815b068c5_nationwidechildrens.org_biospecimen.TCGA-EU-5907.xml";
        HashMap<String, Object> metadata_from_xml = MetadataHandler.getXMLMap(biospecimen_absolute_path);
        XMLNode root = new XMLNode();
        root.setRoot();
        root = MetadataHandler.convertMapToIndexedTree(metadata_from_xml, root);
        
        searchUUIDs(root);
    }
    
    public static void searchUUIDs(XMLNode node) {
        try {
            for (XMLNode n: node.getChilds()) {
                String aliquot_uuid = MetadataHandler.findKey(n.getAttributes(), "endsWith", "bcr_aliquot_uuid");
                if (!aliquot_uuid.trim().equals(""))
                    System.err.println(aliquot_uuid);
                searchUUIDs(n);
            }
        }
        catch (Exception e) {}
    }
    
}
