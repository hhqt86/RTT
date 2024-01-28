package com.example.android.wifirttscan;

public class Config {
    public static int numberOfExaminedAP = 3;
    public static int dataTrainingDuration = 30000; //miliseconds
    public static int dataTestingDuration = 5000;
    static APCoordinateLocation[] apBSSIDLocation= new APCoordinateLocation[]{
            //new APCoordinateLocation("", new Coordinate(0.1,0.1)),

            /* At SMU CIS
            new APCoordinateLocation("1c:28:af:41:46:52", new Coordinate(8.5,1.8)),
            new APCoordinateLocation("1c:28:af:41:89:b2", new Coordinate(22.95,5.4)),
            new APCoordinateLocation("b8:3a:5a:e0:68:52", new Coordinate(24.65,18))*/
            // At home
            new APCoordinateLocation("54:af:97:fb:55:d0", new Coordinate(15.8,4)), //openwrt
            new APCoordinateLocation("5c:e9:31:57:6f:1e", new Coordinate(5.4,8.1)),  //6F1E
            new APCoordinateLocation("5c:e9:31:57:95:ac", new Coordinate(5.4,0))  //95AC

    };

    public static double getDistance(Coordinate c1, Coordinate c2){
        return Math.sqrt((c1.x - c2.x) * (c1.x - c2.x) + (c1.y - c2.y) * (c1.y - c2.y));
    }
}

class APCoordinateLocation {
    public String BSSID;
    public Coordinate location;
    public APCoordinateLocation(String BSSIDinput, Coordinate locationInput){
        BSSID = BSSIDinput;
        location = new Coordinate(locationInput.x, locationInput.y);
    }
}
