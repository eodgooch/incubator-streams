package org.apache.streams.datasift.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.streams.jackson.StreamsJacksonMapper;
import org.apache.streams.pojo.json.Activity;
import org.apache.streams.pojo.json.Actor;
import org.junit.Test;

import java.util.Scanner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by rebanks on 5/29/14.
 */
public class DatasiftActivitySerializerTest {

    private static final DatasiftActivitySerializer SERIALIZER = new DatasiftActivitySerializer();
    private static final ObjectMapper MAPPER = StreamsDatasiftMapper.getInstance();

    @Test
    public void testGeneralConversion() throws Exception {
        Scanner scanner = new Scanner(DatasiftActivitySerializerTest.class.getResourceAsStream("/rand_sample_datasift_json.txt"));
        String line = null;
        while(scanner.hasNextLine()) {
            line = scanner.nextLine();
            testGeneralConversion(line);
        }
    }



    /**
     * Test that the minimum number of things that an activity has
     * @param json
     */
    private void testGeneralConversion(String json) throws Exception {
        Activity activity = SERIALIZER.deserialize(json);
        assertNotNull(json, activity);
        assertNotNull(json, activity.getId());
        assertTrue(json, activity.getId().contains("datasift"));
//        assertNotNull(json, activity.getContent());  //Some facebook do not have content
        assertNotNull(json, activity.getPublished());
        assertNotNull(json, activity.getUrl());
        Actor actor = activity.getActor();
        assertNotNull(json, actor);
        //Not all interactions have authors
//        assertNotNull(json, actor.getId());
//        assertNotNull(json+"\n"+MAPPER.writeValueAsString(activity)+"\n", actor.getDisplayName());
    }



}
