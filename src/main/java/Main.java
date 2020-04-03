import java.io.*;
import java.util.*;

import com.google.gson.*;

public class Main {
    public static void main(String[] args){
        // Get aff file location
        Scanner in = new Scanner(System.in);
        System.out.println("aff file location: ");
        String affloc = in.nextLine();

        // Get metadata
        System.out.println("Difficulty name: ");
        String diffname = in.nextLine();

        System.out.println("Level: ");
        int level = in.nextInt();

        System.out.println("Offset: ");
        int offset = in.nextInt();

        // Process aff file
        try {
            // Read every line from the aff file.
            File affFile = new File(affloc);
            ArrayList<String> affContent = new ArrayList<String>();
            String affLine;
            BufferedReader affReader = new BufferedReader(new FileReader(affFile));
            while((affLine = affReader.readLine()) != null){
                affContent.add(affLine);
            }

            // Construct aux variables
            int tickStack = 0;
            int lastArcEnd = 0;
            int[] table = {1, 3, 5, 7};
            ArrayList<Double[]> arclist = new ArrayList<>();
            ArrayList<ArrayList<Double[]>> sortedArcList = new ArrayList<>();
            ArrayList<JsonObject> objList = new ArrayList<>();
            boolean flag = false;

            // Construct banground chart json
            JsonObject chart = new JsonObject();
            JsonArray notes = new JsonArray();
            int originalOffset = 0;

            // Add metadata
            chart.addProperty("difficulty", diffname);
            chart.addProperty("level", level);
            chart.addProperty("offset", offset);

            // Add bpm 150 cause don't want to fuck with timing conversion
            JsonObject bpm = new JsonObject();
            JsonArray beat = new JsonArray();
            beat.add(0);
            beat.add(0);
            beat.add(1);
            bpm.addProperty("type", "BPM");
            bpm.add("beat", beat);
            bpm.addProperty("value", 150.0);
            notes.add(bpm);

            // Parse the hit object
            for(String object : affContent){
                // Four types of hit object and one timing object here. Each line of the aff file represents an object.
                // (Tap, Hold, Arc, ArcTap)
                // ArcTap is always on a "trace" which is a special type of Arc
                // The first two line of the file are audio offset and a separator "-"

                // System.out.println(object);

                if(object.startsWith("AudioOffset")){
                    // Audio offset line. Omitted to avoid the offset difference between the two audio engine.
                    // Still needed to calculate beat position.
                    originalOffset = Integer.parseInt(object.split(":")[1]);

                }else if(object.startsWith("-")){
                    // Separator. Omitted.

                }else if(object.startsWith("arc") || object.startsWith("Arc")){
                    // Arc object. Check for the type first
                    // For now only linear interpolation
                    // TODO: Add interpolation
                    if(object.contains("true")){
                        // This arc object is a trace i.e. not as a hit object.
                        // Check if there is any ArcTap object on it.
                        if(object.contains("arctap")){
                            // Yep.
                            // Get timing for the start and end. Calculate position x and y.
                            int t = object.indexOf("[");
                            String content = object.substring(4, t);
                            String tapArray = object.substring(t + 1, object.length() - 2);
                            String[] parameters = content.split(",");
                            List<String> tapList = Arrays.asList(tapArray.split(","));
                            double startT = Double.parseDouble(parameters[0]);
                            double endT = Double.parseDouble(parameters[1]);
                            double startX = Double.parseDouble(parameters[2]);
                            double endX = Double.parseDouble(parameters[3]);
                            double startY = Double.parseDouble(parameters[5]);
                            double endY = Double.parseDouble(parameters[6]);
                            for(String tap : tapList){
                                int time = Integer.parseInt(tap.substring(7, tap.length() - 1));
                                double intX = startX;
                                double intY = startY;
                                if(startX != endX){
                                    double kx = (endT - startT) / (endX - startX);
                                    double bx = (endT - kx * endX);
                                    intX = ((time - bx) / kx);
                                }
                                if(startY != endY){
                                    double ky = (endT - startT) / (endY - startY);
                                    double by = (endT - ky * endY);
                                    intY = ((time - by) / ky);
                                }
                                JsonObject arctap = new JsonObject();
                                JsonArray beats = new JsonArray();
                                int position = time + originalOffset;
                                beats.add(0);
                                beats.add(position);
                                beats.add(400);
                                arctap.addProperty("type", "Single");
                                arctap.addProperty("lane", -1); // enable fuwafuwa lane
                                arctap.addProperty("x", (intX * 2) + 3); // BanGroundX = 2 * intX + 3
                                arctap.addProperty("y", intY); // BanGroundY = intY
                                arctap.add("beat", beats);
                                objList.add(arctap);
                            }
                        }

                    }else{
                        // This arc object is a hittable normal object and will be converted to a slider.
                        String content = object.substring(4, object.length() - 1);
                        String[] parameters = content.split(",");
                        double startT = Double.parseDouble(parameters[0]);
                        double endT = Double.parseDouble(parameters[1]);
                        double startX = Double.parseDouble(parameters[2]);
                        double endX = Double.parseDouble(parameters[3]);
                        double startY = Double.parseDouble(parameters[5]);
                        double endY = Double.parseDouble(parameters[6]);

                        Double[] item = {startT, endT, startX, endX, startY, endY};
                        arclist.add(item); // Deal with them later.
                    }

                }else if(object.startsWith("(")){
                    // Tap object.
                    String content = object.substring(1, object.length() - 2);
                    String[] parameters = content.split(",");
                    JsonObject tap = new JsonObject();
                    JsonArray beats = new JsonArray();
                    int position = Integer.parseInt(parameters[0]) + originalOffset;
                    beats.add(0);
                    beats.add(position);
                    beats.add(400);
                    tap.addProperty("type", "Single");
                    tap.addProperty("lane", table[Integer.parseInt(parameters[1]) - 1]);
                    tap.add("beat", beats);
                    objList.add(tap);

                }else if(object.startsWith("hold") || object.startsWith("Hold")){
                    // Hold object.
                    String content = object.substring(5, object.length() - 2);
                    String[] parameters = content.split(",");

                    JsonObject hold1 = new JsonObject();
                    JsonArray beats1 = new JsonArray();
                    int position1 = Integer.parseInt(parameters[0]) + originalOffset;
                    beats1.add(0);
                    beats1.add(position1);
                    beats1.add(400);
                    hold1.addProperty("type", "Single");
                    hold1.addProperty("lane", table[Integer.parseInt(parameters[2]) - 1]);
                    hold1.add("beat", beats1);
                    hold1.addProperty("tickStack", tickStack);

                    JsonObject hold2 = new JsonObject();
                    JsonArray beats2 = new JsonArray();
                    int position2 = Integer.parseInt(parameters[1]) + originalOffset;
                    beats2.add(0);
                    beats2.add(position2);
                    beats2.add(400);
                    hold2.addProperty("type", "SlideTickEnd");
                    hold2.addProperty("lane", table[Integer.parseInt(parameters[2]) - 1]);
                    hold2.add("beat", beats2);
                    hold2.addProperty("tickStack", tickStack);

                    objList.add(hold1);
                    objList.add(hold2);
                    tickStack++;

                }else if(object.startsWith("timing") || object.startsWith("Timing")){
                    // Fuck this timing im done
                    // BPM is constant 150 whatever

                }else{
                    // Unknown object. (Or camera object which don't support in BanGround now.)

                }
            }

            // Deal with arc list
            // Sort first
            /*for(Double[] item : arclist){ // {startT, endT, startX, endX, startY, endY}
                if(sortedArcList.size() == 0){
                    System.out.println("First one.");
                    ArrayList<Double[]> newArc = new ArrayList<>();
                    newArc.add(item);
                    sortedArcList.add(newArc);
                }else{
                    for(ArrayList<Double[]> arcGroup : sortedArcList){
                        double endX = arcGroup.get(arcGroup.size() - 1)[3];
                        double endY = arcGroup.get(arcGroup.size() - 1)[5];
                        System.out.println(endX + ":" + item[2]);
                        System.out.println(endY + ":" + item[4]);
                        if(item[2] == endX && item[4] == endY) {
                            System.out.println("Matched.");
                            arcGroup.add(item);
                            flag = true;
                            break;
                        }
                        System.out.println("Next group.");
                    }
                    if(!flag){
                        System.out.println("Nothing matched. Add a new group.");
                        ArrayList<Double[]> newArc = new ArrayList<>();
                        newArc.add(item);
                        sortedArcList.add(newArc);
                    }
                    System.out.println("");
                    flag = false;
                }
            }*/
            // then assign tickStack and write to notes block
            /*for(ArrayList<Double[]> arcGroup : sortedArcList){
                for(Double[] arcItem : arcGroup){
                    if(arcGroup.indexOf(arcItem) != arcGroup.size() - 1 && arcGroup.indexOf(arcItem) != 0){
                        // Not the last one and not the first one
                        JsonObject arcMiddle = new JsonObject();
                        JsonArray timing1 = new JsonArray();
                        timing1.add(0);
                        timing1.add(arcItem[0].intValue());
                        timing1.add(400);
                        arcMiddle.addProperty("type", "SlideTick");
                        arcMiddle.add("beat", timing1);
                        arcMiddle.addProperty("lane", -1);
                        arcMiddle.addProperty("x", arcItem[2]);
                        arcMiddle.addProperty("y", arcItem[4]);
                        arcMiddle.addProperty("tickStack", tickStack);

                        JsonObject arcEnd = new JsonObject();
                        JsonArray timing2 = new JsonArray();
                        timing2.add(0);
                        timing2.add(arcItem[1].intValue());
                        timing2.add(400);
                        arcEnd.addProperty("type", "SlideTick");
                        arcEnd.add("beat", timing2);
                        arcEnd.addProperty("lane", -1);
                        arcEnd.addProperty("x", arcItem[3]);
                        arcEnd.addProperty("y", arcItem[5]);
                        arcEnd.addProperty("tickStack", tickStack);

                        objList.add(arcMiddle);
                        objList.add(arcEnd);
                    }
                    else if(arcGroup.indexOf(arcItem) == 0){
                        // the first one
                        JsonObject arcMiddle = new JsonObject();
                        JsonArray timing1 = new JsonArray();
                        timing1.add(0);
                        timing1.add(arcItem[0].intValue());
                        timing1.add(400);
                        arcMiddle.addProperty("type", "Single");
                        arcMiddle.add("beat", timing1);
                        arcMiddle.addProperty("lane", -1);
                        arcMiddle.addProperty("x", arcItem[2]);
                        arcMiddle.addProperty("y", arcItem[4]);
                        arcMiddle.addProperty("tickStack", tickStack);

                        JsonObject arcEnd = new JsonObject();
                        JsonArray timing2 = new JsonArray();
                        timing2.add(0);
                        timing2.add(arcItem[1].intValue());
                        timing2.add(400);
                        arcEnd.addProperty("type", "SlideTick");
                        arcEnd.add("beat", timing2);
                        arcEnd.addProperty("lane", -1);
                        arcEnd.addProperty("x", arcItem[3]);
                        arcEnd.addProperty("y", arcItem[5]);
                        arcEnd.addProperty("tickStack", tickStack);

                        objList.add(arcMiddle);
                        objList.add(arcEnd);
                    }else{
                        // Last one
                        JsonObject arcMiddle = new JsonObject();
                        JsonArray timing1 = new JsonArray();
                        timing1.add(0);
                        timing1.add(arcItem[0].intValue());
                        timing1.add(400);
                        arcMiddle.addProperty("type", "SlideTick");
                        arcMiddle.add("beat", timing1);
                        arcMiddle.addProperty("lane", -1);
                        arcMiddle.addProperty("x", arcItem[2]);
                        arcMiddle.addProperty("y", arcItem[4]);
                        arcMiddle.addProperty("tickStack", tickStack);

                        JsonObject arcEnd = new JsonObject();
                        JsonArray timing2 = new JsonArray();
                        timing2.add(0);
                        timing2.add(arcItem[1].intValue());
                        timing2.add(400);
                        arcEnd.addProperty("type", "SlideTickEnd");
                        arcEnd.add("beat", timing2);
                        arcEnd.addProperty("lane", -1);
                        arcEnd.addProperty("x", arcItem[3]);
                        arcEnd.addProperty("y", arcItem[5]);
                        arcEnd.addProperty("tickStack", tickStack);

                        objList.add(arcMiddle);
                        objList.add(arcEnd);

                        tickStack++;
                    }
                }
            }*/

            objList.sort(Comparator.comparingInt(o -> o.get("beat").getAsJsonArray().get(1).getAsInt()));
            for(JsonObject obj : objList){
                notes.add(obj);
            }

            chart.add("notes", notes);

            // Write converted json to file
            System.out.println(chart.toString());
        } catch (FileNotFoundException e){
            System.out.println("ERROR: File not found.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
