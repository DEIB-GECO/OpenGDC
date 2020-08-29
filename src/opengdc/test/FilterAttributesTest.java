/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opengdc.test;

import java.util.ArrayList;
import opengdc.util.MetadataHandler;

/**
 *
 * @author fabio
 */
public class FilterAttributesTest {
    
    public static void main(String[] args) {
        ArrayList<String> attributes = new ArrayList<>();
        attributes.add("manually_curated__associated_entities__case_id");
        attributes.add("manually_curated__cases__case_id");
                
        ArrayList<String> selected_attributes = MetadataHandler.selectAttributes(attributes);
        for (String attr: selected_attributes)
            System.err.println(attr);
        
        attributes = new ArrayList<>();
        attributes.add("manually_curated__associated_entities__entity_id");
        attributes.add("manually_curated__annotations__entity_id");
        
        selected_attributes = MetadataHandler.selectAttributes(attributes);
        for (String attr: selected_attributes)
            System.err.println(attr);
    }
    
}
