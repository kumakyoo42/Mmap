import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.geom.*;
import javax.imageio.*;
import de.kumakyoo.omalibjava.OmaReader;
import de.kumakyoo.omalibjava.Filter;
import de.kumakyoo.omalibjava.AndFilter;
import de.kumakyoo.omalibjava.TypeFilter;
import de.kumakyoo.omalibjava.BlockFilter;
import de.kumakyoo.omalibjava.PolygonFilter;

public class MMap extends Canvas
{
    static final int ZOOM = 20;

    public static void main(String[] args) throws IOException
    {
        new MMap().start(args[0], args[1], args.length>2);
    }

    //////////////////////////////////////////////////////////////////

    String[] nodeList = {"traffic_calming","man_made","highway","barrier","amenity",
                         "pipeline","entrance","playground","traffic_sign",
                         "public_transport","emergency","natural"};

    String[] wayList = {"highway","playground","barrier","public_transport"};

    String[] areaList = {"landuse","amenity","leisure","natural","area:highway",
                         "allotments","man_made","building","playground",
                         "public_transport"};

    Map<String,List<Node>> nodes = new HashMap<>();
    Map<String,List<Way>> ways = new HashMap<>();
    Map<String,List<MP>> areas = new HashMap<>();

    int[] blon;
    int[] blat;

    int xmin, xmax, ymin, ymax;
    double minlon, minlat, maxlon, maxlat;

    public void start(String osmfile, String bb, boolean tiles) throws IOException
    {
        calcBB(bb);
        read(osmfile);
        write(tiles);
    }

    private void calcBB(String bb)
    {
        StringTokenizer t = new StringTokenizer(bb,",");
        blon = new int[t.countTokens()/2];
        blat = new int[t.countTokens()/2];

        double minx = -1;
        double miny = -1;
        double maxx = -1;
        double maxy = -1;

        int c = 0;
        while (t.hasMoreTokens())
        {
            double x = Double.valueOf(t.nextToken());
            double y = Double.valueOf(t.nextToken());

            if (minx==-1 || minx>x) minx=x;
            if (maxx==-1 || maxx<x) maxx=x;
            if (miny==-1 || miny>y) miny=y;
            if (maxy==-1 || maxy<y) maxy=y;

            blon[c] = (int)(x*1e7+0.5);
            blat[c] = (int)(y*1e7+0.5);
            c++;
        }

        xmin = (int)Math.floor(lon2tilesx(minx));
        ymax = (int)Math.floor(lat2tilesy(miny));
        xmax = (int)Math.floor(lon2tilesx(maxx));
        ymin = (int)Math.floor(lat2tilesy(maxy));
        System.err.println("tiles-bbox: "+xmin+" "+ymin+" "+xmax+" "+ymax);

        minlon = Math.floor(tilesx2lon(xmin)*1e6)/1e6;
        minlat = Math.floor(tilesy2lat(ymax+1)*1e6)/1e6;
        maxlon = Math.floor(tilesx2lon(xmax+1)*1e6)/1e6;
        maxlat = Math.floor(tilesy2lat(ymin)*1e6)/1e6;
        System.err.println("bbox: "+minlon+" "+minlat+" "+maxlon+" "+maxlat);
    }

    public void read(String name) throws IOException
    {
        OmaReader f = new OmaReader(name);

        de.kumakyoo.omalibjava.Area a = new de.kumakyoo.omalibjava.Area(blon,blat);
        Filter bb = new PolygonFilter(new de.kumakyoo.omalibjava.Polygon(new de.kumakyoo.omalibjava.Area[] {a}));

        int nc = 0;
        for (String key:nodeList)
        {
            f.setFilter(new AndFilter(bb,new TypeFilter("N"),new BlockFilter(key)));
            List<Node> tmp = new ArrayList<>();
            while (true)
            {
                de.kumakyoo.omalibjava.Element e = f.next();
                if (e==null) break;

                Node n = new Node((de.kumakyoo.omalibjava.Node)e);
                tmp.add(n);
                nc++;
            }
            nodes.put(key,tmp);
        }
        System.err.println("nodes: "+nc);

        int wc = 0;
        for (String key:wayList)
        {
            f.setFilter(new AndFilter(bb,new TypeFilter("W"),new BlockFilter(key)));
            List<Way> tmp = new ArrayList<>();
            while (true)
            {
                de.kumakyoo.omalibjava.Element e = f.next();
                if (e==null) break;

                Way w = new Way((de.kumakyoo.omalibjava.Way)e);
                tmp.add(w);
                wc++;
            }
            ways.put(key,tmp);
        }
        System.err.println("ways: "+wc);

        int ac = 0;
        for (String key:areaList)
        {
            f.setFilter(new AndFilter(bb,new TypeFilter("A"),new BlockFilter(key)));
            List<MP> tmp = new ArrayList<>();
            while (true)
            {
                de.kumakyoo.omalibjava.Element e = f.next();
                if (e==null) break;

                MP m = new MP((de.kumakyoo.omalibjava.Area)e);
                tmp.add(m);
                ac++;
            }
            areas.put(key,tmp);
        }
        System.err.println("areas: "+ac);
    }

    //////////////////////////////////////////////////////////////////

    void write(boolean tiles) throws IOException
    {
        int width = (xmax-xmin+1)*256;
        int height = (ymax-ymin+1)*256;

        BufferedImage b = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = b.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Polygon p = new Polygon();
        for (int i=0;i<blon.length;i++)
            p.addPoint(lon2x(blon[i]/1e7),lat2y(blat[i]/1e7));
        g.clip(new Area(p));

        g.setColor(new Color(0.75f,0.75f,0.75f));
        g.fillRect(0,0,width,height);

        paintLargeLanduse(g);

        paintMediumLanduse(g);
        paintMediumLeisure(g);
        paintMediumAmenity(g);

        paintVehicleWays(g);
        paintTrafficCalming(g);
        paintProtectedStreetAreas(g);
        paintCrossings(g);
        paintPedestrianWays(g);

        paintSmallLanduse(g);
        paintSmallLeisure(g);
        paintSmallAmenity(g);
        paintSmallNatural(g);

        paintAllotments(g);
        paintManMade(g);
        paintBuilding(g);
        paintPlayground(g);
        paintBarriers(g);
        paintPublicTransport(g);

        paintNodes(g);

        if (tiles)
        {
            new File("20").mkdirs();
            for (int j=ymin;j<=ymax;j++)
            {
                new File("20/"+j).mkdirs();
                for (int i=xmin;i<=xmax;i++)
                    ImageIO.write(b.getSubimage(256*(i-xmin),256*(j-ymin),256,256), "png",
                                  new File("20/"+j+"/"+i+".png"));
            }
        }
        else
            ImageIO.write(b, "png", new File("image.png"));
    }

    void paintLargeLanduse(Graphics2D g)
    {
        for (MP a: areas.get("landuse"))
        {
            if ("residential".equals(a.value))
                a.fill(g,new Color(0.9f,0.95f,0.9f));
            if ("commercial".equals(a.value))
                a.fill(g,new Color(0.95f,0.9f,0.9f));
            if ("industrial".equals(a.value))
                a.fill(g,new Color(0.92f,0.85f,0.85f));
            if ("forest".equals(a.value))
                a.fill(g,new Color(0.64f,0.77f,0.64f));
        }
    }

    void paintMediumLanduse(Graphics2D g)
    {
        for (MP a: areas.get("landuse"))
        {
            if ("orchard".equals(a.value))
                a.fill(g,new Color(0.77f,0.92f,0.85f));
            if ("garages".equals(a.value))
                a.fill(g,new Color(0.81f,0.79f,0.77f));
            if ("allotments".equals(a.value))
                a.fill(g,new Color(0.79f,0.88f,0.75f));
        }
    }

    void paintSmallLanduse(Graphics2D g)
    {
        for (MP a: areas.get("landuse"))
        {
            if ("flowerbed".equals(a.value))
                a.fill(g,new Color(0.85f,0.92f,0.77f));
            if ("meadow".equals(a.value))
                a.fill(g,new Color(0.77f,0.92f,0.77f));
            if ("grass".equals(a.value))
                a.fill(g,new Color(0.80f,0.94f,0.80f));
        }
    }

    void paintMediumAmenity(Graphics2D g)
    {
        for (MP a: areas.get("amenity"))
        {
            if ("parking".equals(a.value)
                || "motorcycle_parking".equals(a.value))
                a.fill(g,new Color(0.67f,0.67f,0.67f));
            if ("recycling".equals(a.value))
                a.fill(g,new Color(0.95f,0.84f,0.84f));
            if ("school".equals(a.value))
                a.fill(g,new Color(0.95f,0.95f,0.84f));
        }
    }

    void paintSmallAmenity(Graphics2D g)
    {
        for (MP a: areas.get("amenity"))
        {
            if ("bicycle_parking".equals(a.value))
                a.fill(g,new Color(1f,0.7f,0.7f,0.5f));
            if ("parking_space".equals(a.value))
                a.drawInsets(g,2.5f,Color.WHITE);
            if ("shelter".equals(a.value))
                a.fill(g,new Color(0.85f,0.85f,0.9f));
            if ("taxi".equals(a.value))
                a.drawInsets(g,2.5f,Color.WHITE);
            if ("parcel_locker".equals(a.value))
            {
                a.fill(g,Color.YELLOW);
                a.draw(g,Color.BLACK);
            }
            if ("charging_station".equals(a.value))
                a.fillWithStripes(g,new Color(0f,0.6f,0.85f,0.5f));
        }
    }

    void paintMediumLeisure(Graphics2D g)
    {
        for (MP a: areas.get("leisure"))
        {
            if ("park".equals(a.value))
                a.fill(g,new Color(0.77f,0.92f,0.77f));
            if ("sports_centre".equals(a.value))
                a.fill(g,new Color(0.88f,0.99f,0.89f));
        }
    }

    void paintSmallLeisure(Graphics2D g)
    {
        for (MP a: areas.get("leisure"))
        {
            if ("playground".equals(a.value))
            {
                a.fill(g,new Color(0.9f,0.9f,0.8f));
                a.draw(g,Color.WHITE);
            }
            if ("pitch".equals(a.value))
            {
                a.fill(g,new Color(0.64f,0.88f,0.64f));
                a.draw(g,Color.WHITE);
            }
            if ("track".equals(a.value))
                a.fill(g,new Color(0.90f,0.60f,0.56f));
            if ("outdoor_seating".equals(a.value))
                a.fill(g,new Color(1f,0.95f,0.85f));
        }
    }

    void paintSmallNatural(Graphics2D g)
    {
        for (MP a: areas.get("natural"))
        {
            if ("shrubbery".equals(a.value))
                a.fill(g,new Color(0.85f,0.92f,0.77f));
            if ("scrub".equals(a.value))
                a.fill(g,new Color(0.85f,0.92f,0.77f));
            if ("water".equals(a.value))
                a.fill(g,new Color(0.67f,0.83f,0.88f));
        }
    }

    void paintVehicleWays(Graphics2D g)
    {
        for (MP a: areas.get("area:highway"))
        {
            if ("footway".equals(a.value)
                || "steps".equals(a.value)
                || "path".equals(a.value)
                || "pedestrian".equals(a.value)
                || "traffic_island".equals(a.value)
                || "living_street".equals(a.value)
                || "ramp".equals(a.value))
                continue;

            // Der Untergrund von area:highway=prohibited wird hier auch schon gezeichnet
            if (a.value!=null)
                a.fill(g,new Color(0.64f,0.64f,0.64f));
        }

        for (MP a: areas.get("amenity"))
            if ("taxi".equals(a.value))
                a.fill(g,new Color(0.64f,0.64f,0.64f));
    }

    void paintProtectedStreetAreas(Graphics2D g)
    {
        for (MP a: areas.get("area:highway"))
        {
            if ("traffic_island".equals(a.value))
                a.fill(g,new Color(0.77f,0.77f,0.77f));

            if ("prohibited".equals(a.value))
            {
                if ("DE:298".equals(a.value))
                {
                    a.fill(g,new Color(0.64f,0.64f,0.64f));
                    a.draw(g,Color.WHITE);
                    a.fillWithStripes(g,Color.WHITE);
                }
                else
                {
                    a.fill(g,new Color(0.85f,0.85f,0.85f));
                    a.draw(g,new Color(0.8f,0.8f,0.8f));
                }
            }
        }
    }

    void paintCrossings(Graphics2D g)
    {
        for (Way w: ways.get("highway"))
            if (("footway".equals(w.value) || "path".equals(w.value))
                && "crossing".equals(w.tags().get("footway")))
                w.drawCrossing(g);
    }

    void paintPedestrianWays(Graphics2D g)
    {
        for (MP a: areas.get("area:highway"))
        {
            if ("footway".equals(a.value)
                || "path".equals(a.value)
                || "pedestrian".equals(a.value))
                a.fill(g,new Color(0.70f,0.70f,1.00f));
            if ("living_street".equals(a.value))
                a.fill(g,new Color(0.67f,0.67f,0.84f));
            if ("ramp".equals(a.value))
                a.fill(g,new Color(0.64f,0.64f,0.95f));
            if ("steps".equals(a.value))
                a.drawSteps(g,new Color(0.70f,0.70f,1.00f));
        }
    }

    void paintAllotments(Graphics2D g)
    {
        for (MP a: areas.get("allotments"))
            if ("plot".equals(a.value))
            {
                a.fill(g,new Color(0.73f,0.82f,0.69f));
                a.draw(g,new Color(0.08f,0.09f,0.07f));
            }
    }

    void paintManMade(Graphics2D g)
    {
        for (MP a: areas.get("man_made"))
        {
            if ("street_cabinet".equals(a.value))
            {
                a.fill(g,new Color(0.9f,0.9f,0.9f));
                a.draw(g,new Color(0.6f,0.6f,0.6f));
            }
            if ("planter".equals(a.value))
            {
                a.fill(g,new Color(0.7f,0.9f,0.7f));
                a.draw(g,new Color(0.4f,0.6f,0.4f));
            }
        }
    }

    void paintBuilding(Graphics2D g)
    {
        for (MP a: areas.get("building"))
            if (a.value!=null)
            {
                a.fill(g,new Color(0.8f,0.8f,0.8f));
                a.draw(g,new Color(0.6f,0.6f,0.6f));
            }
    }

    void paintPlayground(Graphics2D g)
    {
        for (MP a: areas.get("playground"))
            if ("sandpit".equals(a.value))
                a.fill(g,new Color(0.85f,0.85f,0.72f));

        for (MP a: areas.get("playground"))
            if (a.value!=null && !"sandpit".equals(a.value))
                a.fill(g,new Color(0.85f,0.64f,0.64f));

        for (Way w: ways.get("playground"))
            if (w.value!=null)
                w.draw(g,new Color(0.85f,0.64f,0.64f),5);
    }

    void paintBarriers(Graphics2D g)
    {
        for (Way w: ways.get("barrier"))
        {
            if ("hedge".equals(w.value))
                w.draw(g,new Color(0.6f,0.7f,0.6f),5);
            else if ("wall".equals(w.value))
                w.draw(g,new Color(0.7f,0.7f,0.7f),3);
            else if ("fence".equals(w.value))
                w.draw(g,new Color(0.7f,0.7f,0.7f),2);
            else if ("kerb".equals(w.value))
                w.drawDelta(g,1,Color.BLACK,new BasicStroke(2,BasicStroke.CAP_BUTT,BasicStroke.JOIN_BEVEL));
            else if (w.value!=null)
                w.draw(g,new Color(0.7f,0.7f,0.7f),1);
        }
    }

    void paintPublicTransport(Graphics2D g)
    {
        for (Way w: ways.get("public_transport"))
            if ("platform".equals(w.value))
                w.draw(g,new Color(0.95f,0.95f,0.7f),10);
        for (MP a: areas.get("public_transport"))
            if ("platform".equals(a.value))
                a.fill(g,new Color(0.95f,0.95f,0.7f));
    }

    void paintTrafficCalming(Graphics2D g)
    {
        for (Node n:nodes.get("traffic_calming"))
        {
            if ("bump".equals(n.value))
                n.paintBump(g);
            if ("table".equals(n.value))
                n.paintStreetTable(g);
            if ("hump".equals(n.value))
                n.paintHump(g);
        }
    }

    void paintNodes(Graphics2D g)
    {
        for (Way w: ways.get("highway"))
        {
            if (("footway".equals(w.value) || "path".equals(w.value))
                && "crossing".equals(w.tags().get("footway")))
                w.paintPedestrianTrafficLights(g);
        }

        for (Node n:nodes.get("man_made"))
        {
            if ("planter".equals(n.value))
                n.paintPlanter(g);
            if ("charge_point".equals(n.value))
                n.paintChargePoint(g);
        }

        for (Node n:nodes.get("highway"))
        {
            if ("traffic_signals".equals(n.value))
                n.paintTrafficSignals(g);
            if ("traffic_mirror".equals(n.value))
                n.paintTrafficMirror(g);
            if ("speed_display".equals(n.value))
                n.paintSpeedDisplay(g);
            if ("give_way".equals(n.value))
                n.paintGiveWay(g);
        }

        for (Node n:nodes.get("barrier"))
        {
            if ("bollard".equals(n.value))
                n.paintBollard(g);
            else if ("block".equals(n.value))
                n.paintBlock(g);
            else if ("gate".equals(n.value))
                n.paintGate(g);
            else if ("lift_gate".equals(n.value))
                n.paintLiftGate(g);
            else if ("cycle_barrier".equals(n.value))
                n.paintCycleBarrier(g);
            else if (n.value!=null && !"kerb".equals(n.value))
                n.paintBarrier(g);
        }

        for (Node n:nodes.get("amenity"))
        {
            if ("waste_basket".equals(n.value))
                n.paintWasteBasket(g);
            if ("bench".equals(n.value))
                n.paintBench(g);
            if ("parking_entrance".equals(n.value))
                n.paintParkingEntrance(g);
            if ("recycling".equals(n.value))
                n.paintRecycling(g);
            if ("table".equals(n.value))
                n.paintTable(g);
            if ("post_box".equals(n.value))
                n.paintPostBox(g);
            if ("bicycle_parking".equals(n.value))
                n.paintBicycleParking(g);
            if ("passenger_information".equals(n.value))
                n.paintPassangerInformation(g);
            if ("vending_machine".equals(n.value))
                n.paintVendingMachine(g);
            if ("clock".equals(n.value))
                n.paintClock(g);
            if ("toilets".equals(n.value))
                n.paintToilets(g);
        }

        for (Node n:nodes.get("pipeline"))
            if ("valve".equals(n.value))
                n.paintValve(g);

        for (Node n:nodes.get("entrance"))
            if (n.value!=null)
                n.paintEntrance(g);

        for (Node n:nodes.get("playground"))
            if (n.value!=null)
                n.paintPlayground(g);

        for (Node n:nodes.get("traffic_sign"))
        {
            if ("give_way".equals(n.value))
                n.paintGiveWay(g);
            if ("DE:306".equals(n.value))
                n.paintPriorityRoad(g);
            if ("DE:301".equals(n.value))
                n.paintPrioritySign(g);
            if ("stop".equals(n.value))
                n.paintStopSign(g);
        }

        for (Node n:nodes.get("public_transport"))
            if ("platform".equals(n.value))
               n.paintBusStop(g);

        for (Node n:nodes.get("highway"))
            if ("bus_stop".equals(n.value))
               n.paintBusStop(g);

        for (Node n:nodes.get("man_made"))
            if ("manhole".equals(n.value))
            {
                if ("sewer".equals(n.tags().get("manhole")))
                    n.paintSewer(g);
                if ("drain".equals(n.tags().get("manhole")))
                    n.paintDrain(g);
                if ("plain".equals(n.tags().get("manhole")))
                    n.paintPlain(g);
            }

        for (Node n:nodes.get("emergency"))
            if ("fire_hydrant".equals(n.value))
                n.paintFireHydrant(g);

        for (Node n:nodes.get("natural"))
            if ("tree".equals(n.value))
                n.paintTree(g);

        for (Node n:nodes.get("highway"))
            if ("street_lamp".equals(n.value))
            {
                if ("suspended".equals(n.tags().get("lamp_mount")))
                    n.paintSuspendedStreetLamp(g);
                else
                    n.paintStreetLamp(g);
            }

        for (Node n:nodes.get("man_made"))
        {
            if ("flagpole".equals(n.value))
                n.paintFlagPole(g);
            if ("parasol".equals(n.value))
                n.paintParasol(g);
            if ("surveillance".equals(n.value))
                n.paintSurveillance(g);
        }
    }

    //////////////////////////////////////////////////////////////////

    float getRotationOfNextStroke(Node n)
    {
        double mindist = Double.MAX_VALUE;
        double bestangle = 0.0f;

        double x = n.x();
        double y = n.y();

        for (List<Way> ws:ways.values())
            for (Way w: ws)
        {
            for (int i=0;i<w.nds().size()-1;i++)
            {
                Node n1 = w.nds().get(i);
                Node n2 = w.nds().get(i+1);

                double x1 = n1.x();
                double y1 = n1.y();
                double x2 = n2.x();
                double y2 = n2.y();

                double ax = x2-x1;
                double ay = y2-y1;

                double lambda = ((x-x1)*ax + (y-y1)*ay) / (ax*ax+ay*ay);
                if (lambda>=0 && lambda<=1)
                {
                    double lx = x1+lambda*ax;
                    double ly = y1+lambda*ay;

                    double dist = dist(x,y,lx,ly);

                    if (dist<mindist)
                    {
                        mindist = dist;
                        bestangle = angle(x1,y1,x2,y2);
                    }
                }
            }
        }

        return (float)Math.toDegrees(bestangle);
    }

    //////////////////////////////////////////////////////////////////

    class Node
    {
        int x,y;
        Map<String, String> tags;
        String value;

        public Map<String, String> tags()
        {
            return tags;
        }

        public int x()
        {
            return x;
        }

        public int y()
        {
            return y;
        }

        public Node(int x, int y)
        {
            this.x = x;
            this.y = y;
        }

        public Node(de.kumakyoo.omalibjava.Node n)
        {
            if (n!=null)
            {
                x = lon2x(n.lon/1e7);
                y = lat2y(n.lat/1e7);
                this.tags = n.tags;
                this.value = n.key==null?null:n.tags.get(n.key);
            }
        }

        public void paintPedestrianTrafficLights(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-10);

            g.fillRect(x-5,y-30,10,20);
            g.setColor(Color.RED);
            g.fillOval(x-3,y-28,6,6);
            g.setColor(Color.GREEN);
            g.fillOval(x-3,y-20,6,6);
        }

        public void paintTree(Graphics2D g)
        {
            g.setColor(new Color(0.5f,0.92f,0.5f,0.3f));
            g.fillOval(x-20,y-20,40,40);
            g.setColor(new Color(0.48f,0.37f,0.12f));
            g.fillOval(x-2,y-2,4,4);
        }

        public void paintStreetLamp(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-20);
            g.drawLine(x-10,y-25,x,y-20);
            g.setColor(new Color(1f,1f,1f,0.8f));
            g.fillOval(x-15,y-30,10,10);
        }

        public void paintSuspendedStreetLamp(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.fillOval(x-1,y-1,2,2);
            g.setColor(new Color(1f,1f,1f,0.8f));
            g.fillOval(x-5,y-5,10,10);
        }

        public void paintFlagPole(Graphics2D g)
        {
            g.setColor(Color.BLUE);
            g.fillRect(x,y-20,10,5);
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-20);
            g.drawLine(x,y-20,x+10,y-20);
            g.drawLine(x,y-15,x+10,y-15);
            g.drawLine(x+10,y-20,x+10,y-15);
        }

        public void paintParasol(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-15);
            g.drawLine(x+2,y-5,x,y-15);
            g.drawLine(x-2,y-5,x,y-15);
        }

        public void paintSurveillance(Graphics2D g)
        {
            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(10));
            g.setTransform(at);

            g.setColor(Color.BLACK);
            g.fillRect(0,-4,14,8);
            g.fillRect(14,-2,3,4);
            g.drawLine(18,3,25,5);
            g.drawLine(18,-3,25,-5);
            g.setTransform(orig);
        }

        public void paintPlanter(Graphics2D g)
        {
            g.setColor(new Color(0.7f,0.9f,0.7f));
            g.fillOval(x-5,y-5,10,10);
            g.setColor(new Color(0.4f,0.6f,0.4f));
            g.drawOval(x-5,y-5,10,10);
        }

        public void paintChargePoint(Graphics2D g)
        {
            g.setColor(new Color(0f,0.6f,0.85f));
            g.fillRect(x-4,y-8,8,16);
            g.setColor(Color.WHITE);
            g.drawLine(x+1,y-6,x-2,y-3);
            g.drawLine(x-2,y-3,x+1,y-3);
            g.drawLine(x+1,y-3,x-2,y);
        }

        public void paintSewer(Graphics2D g)
        {
            g.setColor(new Color(0.38f,0.36f,0.37f));
            g.fillOval(x-6,y-6,12,12);
            g.setColor(new Color(0.49f,0.47f,0.46f));
            g.fillOval(x-3,y-3,6,6);
        }

        public void paintDrain(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(new Color(0.38f,0.36f,0.37f));
            g.fillRect(-4,-4,8,8);
            g.setColor(Color.BLACK);
            g.drawLine(-3,-3,-3,3);
            g.drawLine(0,-3,0,3);
            g.drawLine(3,-3,3,3);

            g.setTransform(orig);
        }

        public void paintPlain(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(new Color(0.38f,0.36f,0.37f));
            g.fillRect(-8,-5,16,10);

            g.setTransform(orig);
        }

        public void paintTrafficSignals(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-10);

            g.fillRect(x-5,y-38,10,28);
            g.setColor(Color.RED);
            g.fillOval(x-3,y-36,6,6);
            g.setColor(Color.YELLOW);
            g.fillOval(x-3,y-28,6,6);
            g.setColor(Color.GREEN);
            g.fillOval(x-3,y-20,6,6);
        }

        public void paintTrafficMirror(Graphics2D g)
        {
            g.setColor(Color.WHITE);
            g.fillRect(x-5,y-18,10,8);
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-10);
            g.drawRect(x-5,y-18,10,8);
        }

        public void paintSpeedDisplay(Graphics2D g)
        {
            g.setColor(Color.GRAY);
            g.fillRect(x-8,y-5,16,10);
            g.setColor(Color.BLACK);
            g.drawRect(x-8,y-5,16,10);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g.drawString("30",x-5,y+4);
        }

        public void mark(Graphics2D g)
        {
            g.setColor(Color.RED);
            g.fillOval(x-20,y-20,40,40);
        }

        public void paintBollard(Graphics2D g)
        {
            Stroke origStroke = g.getStroke();
            g.setStroke(new BasicStroke(3));

            g.setColor(Color.WHITE);
            g.drawLine(x,y,x,y-15);
            g.setColor(Color.RED);
            g.drawLine(x,y-4,x,y-5);
            g.drawLine(x,y-10,x,y-11);
            g.setStroke(origStroke);
        }

        public void paintBlock(Graphics2D g)
        {
            Stroke origStroke = g.getStroke();
            g.setStroke(new BasicStroke(16,BasicStroke.CAP_BUTT,BasicStroke.JOIN_BEVEL));

            g.setColor(Color.GRAY);
            g.drawLine(x,y,x,y-8);
            g.setStroke(origStroke);
        }

        public void paintGate(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d+90));
            g.setTransform(at);

            g.setColor(Color.BLACK);
            g.drawRect(-10,-8,20,16);
            g.drawLine(-10,-8,10,8);
            g.drawLine(-10,8,10,-8);

            g.setTransform(orig);
        }

        public void paintLiftGate(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d+90));
            g.setTransform(at);

            Stroke origStroke = g.getStroke();
            g.setStroke(new BasicStroke(3));

            g.setColor(new Color(0.8f,0.1f,0.1f));
            g.drawLine(13,4,13,-4);
            g.setColor(Color.WHITE);
            g.drawLine(15,-3,-15,-3);
            g.setColor(Color.RED);
            g.drawLine(-12,-3,-6,-3);
            g.drawLine(6,-3,12,-3);
            g.setStroke(origStroke);

            g.setTransform(orig);
        }

        public void paintCycleBarrier(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x-10+2,y+10-2,x-10+2,y-2);
            g.drawLine(x-10+2,y-2,x+10+2,y-2);
            g.drawLine(x+10+2,y-2,x+10+2,y+10-2);
            g.drawLine(x-10+8,y+10-8,x-10+8,y-8);
            g.drawLine(x-10+8,y-8,x+10+8,y-8);
            g.drawLine(x+10+8,y-8,x+10+8,y+10-8);
        }

        public void paintBarrier(Graphics2D g)
        {
            Stroke origStroke = g.getStroke();
            g.setStroke(new BasicStroke(3));

            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-15);
            g.setStroke(origStroke);
        }

        public void paintWasteBasket(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawOval(x-6,y-6,12,4);
            Polygon p = new Polygon();
            p.addPoint(x-6,y-4);
            p.addPoint(x+6,y-4);
            p.addPoint(x+4,y+6);
            p.addPoint(x-4,y+6);
            g.fill(p);
            g.setColor(Color.GRAY);
            g.fillOval(x-6,y-6,12,4);
            g.setColor(Color.BLACK);
            g.drawOval(x-6,y-6,12,4);
        }

        public void paintBench(Graphics2D g)
        {
            int d = 0;
            try {
                d = Integer.parseInt(tags.getOrDefault("direction","0"));
            } catch (Exception e) {}

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(new Color(0.48f,0.37f,0.12f));
            g.drawLine(-8,-5,-8,3);
            g.drawLine(8,-5,8,3);
            g.fillRect(-12,-4,24,1);
            g.fillRect(-12,-2,24,1);
            g.fillRect(-12,0,24,1);
            g.fillRect(-12,2,24,3);
            g.setTransform(orig);
        }

        public void paintParkingEntrance(Graphics2D g)
        {
            g.setColor(new Color(0.05f,0.27f,0.54f));
            g.fillRect(x-10,y-12,20,24);
            g.setColor(Color.WHITE);

            Stroke origStroke = g.getStroke();
            g.setStroke(new BasicStroke(3));
            g.drawLine(x-4,y-8,x-4,y+8);
            g.drawLine(x-4,y-8,x+1,y-8);
            g.drawLine(x-4,y-2,x+1,y-2);
            g.drawArc(x-2,y-8,6,6,-90,180);
            g.setStroke(origStroke);
        }

        public void paintRecycling(Graphics2D g)
        {
            Polygon p1 = new Polygon();
            p1.addPoint(-9,9);
            p1.addPoint(-3,9);
            p1.addPoint(-3,5);
            p1.addPoint(-7,5);
            Polygon p2 = new Polygon();
            p2.addPoint(10,5);
            p2.addPoint(8,9);
            p2.addPoint(3,9);
            p2.addPoint(3,11);
            p2.addPoint(0,7);
            p2.addPoint(3,3);
            p2.addPoint(3,5);

            g.setColor(new Color(0.1f,0.4f,0.05f));
            AffineTransform orig = g.getTransform();
            for (int i=0;i<3;i++)
            {
                AffineTransform at = new AffineTransform();
                at.translate(x,y);
                at.rotate(Math.toRadians(120*i));
                g.setTransform(at);
                g.fill(p1);
                g.fill(p2);
                g.setTransform(orig);
            }
        }

        public void paintTable(Graphics2D g)
        {
            int d = 0;
            try {
                d = Integer.parseInt(tags.getOrDefault("direction","0"));
            } catch (Exception e) {}

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(new Color(0.48f,0.37f,0.12f));
            g.fillRect(-12,-6,24,12);
            g.setTransform(orig);
        }

        public void paintPostBox(Graphics2D g)
        {
            g.setColor(Color.YELLOW);
            g.fillRect(x-10,y-8,20,16);
            g.setColor(Color.WHITE);
            g.fillRect(x-4,y-2,8,8);
            g.setColor(Color.BLACK);
            g.drawRect(x-10,y-8,20,16);
            g.drawLine(x-7,y-5,x+7,y-5);
            g.drawRect(x-4,y-2,8,8);
        }

        public void paintBicycleParking(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y+2,x-2,y-3);
            g.drawLine(x,y+2,x+2,y-3);
            g.drawLine(x-2,y-3,x+2,y-3);
            g.drawLine(x,y+2,x-4,y+2);
            g.drawLine(x-4,y+2,x-2,y-3);
            g.drawLine(x+2,y-3,x+4,y+2);
            g.drawOval(x-6,y,4,4);
            g.drawOval(x+2,y,4,4);
            g.drawLine(x-1,y-4,x,y-4);
            g.drawLine(x+2,y-4,x+4,y-4);
        }

        public void paintPassangerInformation(Graphics2D g)
        {
            g.setColor(Color.GRAY);
            g.fillRect(x-5,y-12,10,7);
            g.setColor(Color.BLACK);
            g.drawLine(x-5,y+6,x-5,y-12);
            g.drawLine(x+5,y+6,x+5,y-12);
            g.drawRect(x-5,y-12,10,7);
        }

        public void paintVendingMachine(Graphics2D g)
        {
            g.setColor(new Color(0.5f,0.7f,0.5f));
            g.fillRect(x-5,y-7,10,14);
            g.setColor(Color.BLACK);
            g.drawRect(x-5,y-7,10,14);
            g.drawRect(x-4,y-6,6,9);
            g.drawLine(x-4,y-3,x+2,y-3);
            g.drawLine(x-4,y-0,x+2,y-0);
            g.drawLine(x-3,y+5,x+3,y+5);
        }

        public void paintClock(Graphics2D g)
        {
            g.setColor(Color.WHITE);
            g.fillOval(x-8,y-8,16,16);
            g.setColor(Color.BLACK);
            g.drawOval(x-8,y-8,16,16);
            g.drawLine(x,y,x,y-8);
            g.drawLine(x,y,x+6,y);
        }

        public void paintToilets(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.PLAIN, 20));
            g.drawString("WC",x-10,y-10);
        }

        public void paintFireHydrant(Graphics2D g)
        {
            g.setColor(new Color(0.58f,0.16f,0.17f));
            g.fillOval(x-4,y-3,8,6);

            String ref = tags.get("ref");
            if (ref==null) return;

            g.setFont(new Font("SansSerif", Font.PLAIN, 8));
            g.drawString(ref,x+6,y+6);
        }

        public void paintValve(Graphics2D g)
        {
            if ("water".equals(tags.get("substance")))
                g.setColor(new Color(0.18f,0.16f,0.57f));
            else
                g.setColor(new Color(0.58f,0.56f,0.17f));
            g.fillRect(x-1,y-1,2,2);
        }

        public void paintEntrance(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(Color.BLACK);
            Polygon p = new Polygon();
            p.addPoint(-10,0);
            p.addPoint(-3,-10);
            p.addPoint(-3,-3);
            p.addPoint(3,-3);
            p.addPoint(3,-10);
            p.addPoint(10,0);
            p.addPoint(3,10);
            p.addPoint(3,3);
            p.addPoint(-3,3);
            p.addPoint(-3,10);
            g.fill(p);

            g.setTransform(orig);
        }

        public void paintPlayground(Graphics2D g)
        {
            g.setColor(new Color(0.85f,0.64f,0.64f));
            g.fillOval(x-10,y-10,20,20);
        }

        public void paintGiveWay(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-10);

            Polygon p = new Polygon();
            p.addPoint(x-8,y-21);
            p.addPoint(x+8,y-21);
            p.addPoint(x,y-10);
            g.setColor(Color.RED);
            g.fill(p);
            p = new Polygon();
            p.addPoint(x-5,y-20);
            p.addPoint(x+5,y-20);
            p.addPoint(x,y-12);
            g.setColor(Color.WHITE);
            g.fill(p);
        }

        public void paintPriorityRoad(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-10);

            Polygon p = new Polygon();
            p.addPoint(x-8,y-19);
            p.addPoint(x,y-26);
            p.addPoint(x+8,y-19);
            p.addPoint(x,y-10);
            g.setColor(Color.WHITE);
            g.fill(p);
            p = new Polygon();
            p.addPoint(x-5,y-18);
            p.addPoint(x,y-24);
            p.addPoint(x+5,y-18);
            p.addPoint(x,y-12);
            g.setColor(new Color(1f,0.8f,0f));
            g.fill(p);
        }

        public void paintStopSign(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-10);

            Polygon p = new Polygon();
            p.addPoint(x-4,y-8);
            p.addPoint(x+4,y-8);
            p.addPoint(x+8,y-12);
            p.addPoint(x+8,y-20);
            p.addPoint(x+4,y-24);
            p.addPoint(x-4,y-24);
            p.addPoint(x-8,y-20);
            p.addPoint(x-8,y-12);
            g.setColor(Color.RED);
            g.fill(p);
            g.setColor(Color.WHITE);
            g.draw(p);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.PLAIN, 6));
            g.drawString("STOP",x-7,y-13);
        }

        public void paintPrioritySign(Graphics2D g)
        {
            g.setColor(Color.BLACK);
            g.drawLine(x,y,x,y-10);

            Polygon p = new Polygon();
            p.addPoint(x-8,y-10);
            p.addPoint(x+8,y-10);
            p.addPoint(x,y-24);
            g.setColor(Color.RED);
            g.fill(p);
            p = new Polygon();
            p.addPoint(x-5,y-12);
            p.addPoint(x+5,y-12);
            p.addPoint(x,y-21);
            g.setColor(Color.WHITE);
            g.fill(p);

            g.setColor(Color.BLACK);
            g.fillRect(x-1,y-17,2,4);
            g.drawLine(x-2,y-16,x+2,y-16);
        }

        public void paintBump(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(new Color(0.9f,0.9f,0.6f));
            if ("yes".equals(tags.get("bicycle_bypass")))
            {
                g.fillOval(-4,-20,8,16);
                g.fillOval(-4,4,8,16);
            }
            else
                g.fillOval(-4,-20,8,40);

            g.setTransform(orig);
        }

        public void paintHump(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(new Color(0.7f,0.7f,0.7f));
            g.fillRect(-20,-40,40,80);
            g.setColor(new Color(0.8f,0.8f,0.8f));
            g.fillRect(-20,-40,40,4);
            g.fillRect(-20,36,40,4);

            g.setTransform(orig);
        }

        public void paintStreetTable(Graphics2D g)
        {
            float d = getRotationOfNextStroke(this);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(x,y);
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(new Color(0.8f,0.8f,0.8f));
            g.fillRect(-20,-20,40,40);
            g.setColor(new Color(0.6f,0.6f,0.6f));
            g.fillRect(-16,-16,32,32);

            g.setTransform(orig);
        }

        public void paintBusStop(Graphics2D g)
        {
            g.setColor(new Color(0.4f,0.6f,0.3f));
            g.fillOval(x-10,y-10,20,20);
            g.setColor(new Color(0.9f,0.9f,0.2f));
            g.fillOval(x-8,y-8,16,16);
            g.setColor(new Color(0.4f,0.6f,0.3f));
            g.drawLine(x-3,y,x+3,y);
            g.drawLine(x-3,y-5,x-3,y+5);
            g.drawLine(x+3,y-5,x+3,y+5);
        }
    }

    class Way
    {
        Map<String, String> tags = null;
        List<Node> nds = null;
        String value;

        public Map<String, String> tags()
        {
            return tags;
        }

        public List<Node> nds()
        {
            return nds;
        }

        public void addNode(Node n)
        {
            if (nds.size()>0)
            {
                Node last = nds.get(nds.size()-1);
                if (last.x()==n.x() && last.y()==n.y())
                    return;
            }
            nds.add(n);
        }

        public Way()
        {
            nds = new ArrayList<>();
        }

        public Way(de.kumakyoo.omalibjava.Way w)
        {
            this.tags = w.tags;
            this.value = w.key==null?null:w.tags.get(w.key);
            nds = new ArrayList<>();
            for (int i=0;i<w.lon.length;i++)
                addNode(new Node(lon2x(w.lon[i]/1e7),lat2y(w.lat[i]/1e7)));
        }

        public void draw(Graphics2D g, Color c)
        {
            this.draw(g,c,1);
        }

        public void draw(Graphics2D g, Color c, int width)
        {
            draw(g,c,new BasicStroke(width));
        }

        public void draw(Graphics2D g, Color c, BasicStroke s)
        {
            boolean first = true;
            Path2D p = new Path2D.Float();
            for (Node n:nds)
            {
                if (first)
                    p.moveTo(n.x(),n.y());
                else
                    p.lineTo(n.x(),n.y());
                first = false;
            }

            Stroke origStroke = g.getStroke();
            g.setStroke(s);
            g.setColor(c);
            g.draw(p);
            g.setStroke(origStroke);
        }

        public void drawDelta(Graphics2D g, int delta, Color c, BasicStroke s)
        {
            Stroke origStroke = g.getStroke();
            g.setStroke(s);
            g.setColor(c);

            boolean first = true;
            Path2D p = new Path2D.Double();
            for (int i=0;i<nds.size()-1;i++)
            {
                Node n1 = nds.get(i);
                Node n2 = nds.get(i+1);
                double x1 = n1.x();
                double y1 = n1.y();
                double x2 = n2.x();
                double y2 = n2.y();

                double d = dist(x1,y1,x2,y2)/delta;
                double dx = (y1-y2)/d;
                double dy = (x2-x1)/d;

                if (first)
                {
                    p.moveTo((int)(x1+dx),(int)(y1+dy));
                    first = false;
                }

                p.lineTo((int)(x1+dx),(int)(y1+dy));
                p.lineTo((int)(x2+dx),(int)(y2+dy));
            }
            g.draw(p);
            g.setStroke(origStroke);
        }

        private String getCrossingType()
        {
            String type = null;
            for (Node n:nds)
                for (Node n2:nodes.get("highway"))
                    if (n2.x()==n.x() && n2.y()==n.y())
                        if (n2.tags()!=null)
                            type = n2.tags().getOrDefault("crossing",type);
            return type;
        }

        public void drawCrossing(Graphics2D g)
        {
            String type = getCrossingType();

            int width = 30;
            if ("informal".equals(type))
                width = 10;
            if ("unmarked".equals(type) || "uncontrolled".equals(type))
                width = 20;

            Stroke origStroke = g.getStroke();
            draw(g,new Color(0.7f,0.7f,1f,0.64f),width);

            if ("marked".equals(type))
            {
                float[] h = new float[2];
                h[0] = 2.0f;
                h[1] = 5.0f;
                draw(g,Color.WHITE,new BasicStroke(width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_BEVEL,0,h,0));
            }

            if ("traffic_signals".equals(type))
            {
                float[] h = new float[2];
                h[0] = 6.0f;
                h[1] = 15.0f;
                drawDelta(g,width/2,Color.WHITE,new BasicStroke(3,BasicStroke.CAP_BUTT,BasicStroke.JOIN_BEVEL,0,h,0));
                drawDelta(g,-width/2,Color.WHITE,new BasicStroke(3,BasicStroke.CAP_BUTT,BasicStroke.JOIN_BEVEL,0,h,0));
            }

            g.setStroke(origStroke);
        }

        public void paintPedestrianTrafficLights(Graphics2D g)
        {
            String type = getCrossingType();

            if ("traffic_signals".equals(type))
            {
                nds.get(0).paintPedestrianTrafficLights(g);
                nds.get(nds.size()-1).paintPedestrianTrafficLights(g);
            }
        }
    }

    class MP
    {
        Map<String, String> tags = null;
        Way outer = null;
        List<Way> inner = null;
        String value;

        public Map<String, String> tags()
        {
            return tags;
        }

        public MP(de.kumakyoo.omalibjava.Area a)
        {
            this.tags = a.tags;
            this.value = a.key==null?null:a.tags.get(a.key);

            inner = new ArrayList<>();

            Way w = new Way();
            for (int i=0;i<a.lon.length;i++)
                w.addNode(new Node(lon2x(a.lon[i]/1e7),lat2y(a.lat[i]/1e7)));
            outer = w;

            for (int k=0;k<a.holes_lon.length;k++)
            {
                w = new Way();
                for (int i=0;i<a.holes_lon[k].length;i++)
                    w.addNode(new Node(lon2x(a.holes_lon[k][i]/1e7),lat2y(a.holes_lat[k][i]/1e7)));
                inner.add(w);
            }
        }

        private Area getArea()
        {
            Area a = new Area();
            Polygon p = new Polygon();
            for (Node n:outer.nds())
                p.addPoint(n.x(),n.y());
            a.add(new Area(p));

            for (Way w: inner)
            {
                p = new Polygon();
                for (Node n:w.nds())
                    p.addPoint(n.x(),n.y());
                a.subtract(new Area(p));
            }
            return a;
        }

        private int[] getBoundingBox()
        {
            int[] bb = new int[4];

            boolean first = true;

            {
                for (Node n:outer.nds())
                {
                    int x1 = n.x();
                    int y1 = n.y();

                    if (first)
                    {
                        bb[0] = bb[2] = x1;
                        bb[1] = bb[3] = y1;
                        first = false;
                    }

                    if (x1<bb[0]) bb[0] = x1;
                    if (y1<bb[1]) bb[1] = y1;
                    if (x1>bb[2]) bb[2] = x1;
                    if (y1>bb[3]) bb[3] = y1;
                }
            }

            for (Way w: inner)
            {
                for (Node n:w.nds())
                {
                    int x1 = n.x();
                    int y1 = n.y();

                    if (first)
                    {
                        bb[0] = bb[2] = x1;
                        bb[1] = bb[3] = y1;
                        first = false;
                    }

                    if (x1<bb[0]) bb[0] = x1;
                    if (y1<bb[1]) bb[1] = y1;
                    if (x1>bb[2]) bb[2] = x1;
                    if (y1>bb[3]) bb[3] = y1;
                }
            }

            return bb;
        }

        public void fill(Graphics2D g, Color c)
        {
            Area a = getArea();

            g.setColor(c);
            g.fill(a);
        }

        public void draw(Graphics2D g, Color c)
        {
            Area a = getArea();

            g.setColor(c);
            g.draw(a);
        }

        public void clip(Graphics2D g)
        {
            g.clip(getArea());
        }

        public void drawInsets(Graphics2D g, float width, Color c)
        {
            Area a = getArea();

            Shape origClip = g.getClip();
            Stroke origStroke = g.getStroke();
            g.clip(a);
            g.setColor(c);
            g.setStroke(new BasicStroke(2*width));
            g.draw(a);
            g.setStroke(origStroke);
            g.setClip(origClip);
        }

        public void fillWithStripes(Graphics2D g, Color c)
        {
            Area a = getArea();
            int[] bb = getBoundingBox();

            Shape origClip = g.getClip();
            Stroke origStroke = g.getStroke();
            g.clip(a);

            g.setStroke(new BasicStroke(3));
            g.setColor(c);
            for (int i=bb[1]-(bb[2]-bb[0]);i<=bb[3];i+=10)
                g.drawLine(bb[0],i,bb[2],i+(bb[2]-bb[0]));

            g.setStroke(origStroke);
            g.setClip(origClip);
        }

        public void drawSteps(Graphics2D g, Color c)
        {
            int[] bb = getBoundingBox();
            Node center = new Node((bb[0]+bb[2])/2,(bb[1]+bb[3])/2);
            double dd = dist(bb[0],bb[1],bb[2],bb[3]);

            float d = getRotationOfNextStroke(center)+90;

            fill(g,c);

            Shape origClip = g.getClip();
            clip(g);

            AffineTransform orig = g.getTransform();
            AffineTransform at = new AffineTransform();
            at.translate(center.x(),center.y());
            at.rotate(Math.toRadians(d));
            g.setTransform(at);

            g.setColor(Color.BLACK);
            for (double i=-dd;i<=dd;i+=5)
                g.drawLine((int)-dd,(int)i,(int)dd,(int)i);

            g.setTransform(orig);
            g.setClip(origClip);
        }
    }

    //////////////////////////////////////////////////////////////////

    double dist(double x1, double y1, double x2, double y2)
    {
        double dx = x2-x1;
        double dy = y2-y1;

        return Math.sqrt(dx*dx+dy*dy);
    }

    double angle(double x1, double y1, double x2, double y2)
    {
        double dx = x2-x1;
        double dy = y2-y1;
        if (dx<0) { dx = -dx; dy = -dy; }
        double n = Math.sqrt(dx*dx+dy*dy);
        if (n==0) return 0.0;
        return Math.asin(dy/n);
    }

    int lon2x(double x)
    {
        return (int)((lon2tilesx(x)-xmin)*256);
    }

    int lat2y(double y)
    {
        return (int)((lat2tilesy(y)-ymin)*256);
    }

    double lon2tilesx(double x)
    {
        return (x+180)/360*(1<<ZOOM);
    }

    double lat2tilesy(double y)
    {
        y = Math.toRadians(y);
        return (1-Math.log(Math.tan(y)+1/Math.cos(y))/Math.PI)/2 * (1<<ZOOM);
    }

    double tilesx2lon(double x)
    {
        return x/(1<<ZOOM)*360-180;
    }

    double tilesy2lat(double y)
    {
        return Math.toDegrees(Math.atan(Math.sinh((1-(y/(1<<ZOOM)*2))*Math.PI)));
    }
}
