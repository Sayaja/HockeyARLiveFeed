package com.axelweinz.hockeyarlivefeed;

import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.schemas.lull.Vec2;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {

    private ModelRenderable hockeyRinkRenderable;
    private ModelRenderable hockeyStickRenderable;
    private ModelRenderable whistleRenderable;
    private ModelRenderable puckRenderable;
    private ViewRenderable shotInfoRenderable;
    private ViewRenderable ejectionInfoRenderable;
    private ViewRenderable ppHomeRenderable;
    private ViewRenderable ppAwayRenderable;
    private ViewRenderable goalInfoRenderable;
    private ViewRenderable faceOffRenderable;
    private ViewRenderable scoreBugRenderable;
    private ViewRenderable statsRenderable;
    private ViewRenderable statsLRenderable;
    private ViewRenderable statsRRenderable;

    private FirebaseDatabase db = FirebaseDatabase.getInstance();
    private DatabaseReference dbShotsRef = db.getReference("shots");
    private DatabaseReference dbEjectionsRef = db.getReference("ejections");
    private DatabaseReference dbGoalsRef = db.getReference("goals");
    private DatabaseReference dbFaceOffsRef = db.getReference("faceoffs");

    private Game game = new Game(); // Game class that contains all general nodes etc

    private Button statsToggle;
    private ImageButton refreshButton;

    private ArFragment arFragment;
    private HitResult firstHit;
    private static final String TAG = MainActivity.class.getSimpleName();
    private Handler handler = new Handler();
    private Runnable eventRunner = new Runnable() { // Generates event
        @Override
        public void run() {
            event();
            handler.postDelayed(eventRunner, 4000);
        }
    };
    private Runnable runScoreBug = new Runnable() { // Updating scoreBug every second
        @Override
        public void run() {
            double secPassed = (System.nanoTime() - game.getGameTime()) / 1_000_000_000.0;
            double minPassed = secPassed / 60;
            int min = 20 - (int)(secPassed/60) - 1;
            int sec = 60 * ((int) minPassed + 1) - (int) secPassed - 1;
            String clock;
            if (sec < 10) {
                clock = Integer.toString(min) + ":0" + Integer.toString(sec);
            } else {
                clock = Integer.toString(min) + ":" + Integer.toString(sec);
            }

            if (!stats && rinkPlaced) {
                String text = "<font color="+game.getHomeColor()+">" + game.getHomeTeam() + "</font> <font color=#ffffff>"
                        + game.getHomeScore() + " - " + game.getAwayScore() + "</font> <font color="+game.getAwayColor()+">"
                        + game.getAwayTeam() + "</font> <font color=#ffffff>" + clock + "</font>";
                game.getScoreText().setText(Html.fromHtml(text));
            } else {
                game.getScoreText().setText("");
            }

            CompletableFuture<ViewRenderable> scoreStage =
                    ViewRenderable.builder().setView(getApplicationContext(), game.getScoreText()).build();

            CompletableFuture.allOf(
                    scoreStage)
                    .handle(
                            (notUsed, throwable) -> {
                                if (throwable != null) {
                                    return null;
                                }

                                try {
                                    scoreBugRenderable = scoreStage.get();

                                    scoreBugRenderable.setShadowCaster(false);

                                    try { // Remove old scoreBug
                                        game.getScoreBug().getScene().onRemoveChild(game.getScoreBug().getParent());
                                        game.getScoreBug().setRenderable(null);
                                        game.getScoreBugNode().getAnchor().detach();
                                    } catch (NullPointerException e) {
                                    }

                                    // Create the Anchor.
                                    Anchor anchor = firstHit.createAnchor();
                                    game.setScoreBugNode(new AnchorNode(anchor));
                                    game.getScoreBugNode().setParent(arFragment.getArSceneView().getScene());

                                    game.setScoreBug(new TransformableNode(arFragment.getTransformationSystem()));
                                    game.getScoreBug().setRenderable(scoreBugRenderable);

                                    game.getScoreBug().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.3f, rinkPos.z - 0.35f));

                                    game.getScoreBug().setParent(game.getScoreBugNode());

                                    handler.postDelayed(this, 1000);
                                } catch (InterruptedException | ExecutionException ex) {
                                }

                                return null;
                            });
        }
    };
    private Runnable ppHomeRun = new Runnable() {
        @Override
        public void run() {
            double secPassed = (System.nanoTime() - game.getHomePPStart()) / 1_000_000_000.0;
            double minPassed = secPassed / 60;
            int min = 2 - (int)(secPassed/60) - 1;
            int sec = 60 * ((int) minPassed + 1) - (int) secPassed - 1;
            String clock;
            if (sec < 10) {
                clock = Integer.toString(min) + ":0" + Integer.toString(sec);
            } else {
                clock = Integer.toString(min) + ":" + Integer.toString(sec);
            }

            try { // Clear PP anchors
                game.getHomePP().getScene().onRemoveChild(game.getHomePP().getParent());
                game.getHomePP().setRenderable(null);
                game.getHomePPNode().getAnchor().detach();
            } catch (NullPointerException e) {
            }

            if (secPassed < 120) {
                if (!stats && rinkPlaced) {
                    String text = "<font color="+game.getHomeColor()+">" + "PP</font> <font color=#ffffff>"+clock+"</font>";
                    game.getHomePPText().setText(Html.fromHtml(text));
                } else {
                    game.getHomePPText().setText("");
                }

                CompletableFuture<ViewRenderable> ppStage =
                        ViewRenderable.builder().setView(getApplicationContext(), game.getHomePPText()).build();

                CompletableFuture.allOf(
                        ppStage)
                        .handle(
                                (notUsed, throwable) -> {
                                    if (throwable != null) {
                                        return null;
                                    }

                                    try {
                                        ppHomeRenderable = ppStage.get();

                                        ppHomeRenderable.setShadowCaster(false);

                                        // Create the Anchor.
                                        Anchor anchor = firstHit.createAnchor();
                                        game.setHomePPNode(new AnchorNode(anchor));
                                        game.getHomePPNode().setParent(arFragment.getArSceneView().getScene());

                                        game.setHomePP(new TransformableNode(arFragment.getTransformationSystem()));
                                        game.getHomePP().setRenderable(ppHomeRenderable);

                                        game.getHomePP().setLocalPosition(new Vector3(rinkPos.x - 0.185f, rinkPos.y + 0.23f, rinkPos.z - 0.35f));

                                        game.getHomePP().setParent(game.getHomePPNode());

                                        handler.postDelayed(this, 1000);
                                    } catch (InterruptedException | ExecutionException ex) {
                                    }

                                    return null;
                                });
            } else { // PP ended
                game.setHomePPBool(false);
            }
        }
    };
    private Runnable ppAwayRun = new Runnable() {
        @Override
        public void run() {
            double secPassed = (System.nanoTime() - game.getAwayPPStart()) / 1_000_000_000.0;
            double minPassed = secPassed / 60;
            int min = 2 - (int)(secPassed/60) - 1;
            int sec = 60 * ((int) minPassed + 1) - (int) secPassed - 1;
            String clock;
            if (sec < 10) {
                clock = Integer.toString(min) + ":0" + Integer.toString(sec);
            } else {
                clock = Integer.toString(min) + ":" + Integer.toString(sec);
            }

            try { // Clear PP anchors
                game.getAwayPP().getScene().onRemoveChild(game.getAwayPP().getParent());
                game.getAwayPP().setRenderable(null);
                game.getAwayPPNode().getAnchor().detach();
            } catch (NullPointerException e) {
            }

            if (secPassed < 120) {
                if (!stats && rinkPlaced) {
                    String text = "<font color="+game.getAwayColor()+">" + "PP</font> <font color=#ffffff>"+clock+"</font>";
                    game.getAwayPPText().setText(Html.fromHtml(text));
                } else {
                    game.getAwayPPText().setText("");
                }

                CompletableFuture<ViewRenderable> ppStage =
                        ViewRenderable.builder().setView(getApplicationContext(), game.getAwayPPText()).build();

                CompletableFuture.allOf(
                        ppStage)
                        .handle(
                                (notUsed, throwable) -> {
                                    if (throwable != null) {
                                        return null;
                                    }

                                    try {
                                        ppAwayRenderable = ppStage.get();

                                        ppAwayRenderable.setShadowCaster(false);

                                        // Create the Anchor.
                                        Anchor anchor = firstHit.createAnchor();
                                        game.setAwayPPNode(new AnchorNode(anchor));
                                        game.getAwayPPNode().setParent(arFragment.getArSceneView().getScene());

                                        game.setAwayPP(new TransformableNode(arFragment.getTransformationSystem()));
                                        game.getAwayPP().setRenderable(ppAwayRenderable);

                                        game.getAwayPP().setLocalPosition(new Vector3(rinkPos.x + 0.15f, rinkPos.y + 0.23f, rinkPos.z - 0.35f));

                                        game.getAwayPP().setParent(game.getAwayPPNode());

                                        handler.postDelayed(this, 1000);
                                    } catch (InterruptedException | ExecutionException ex) {
                                    }

                                    return null;
                                });
            } else { // PP ended
                game.setAwayPPBool(false);
            }
        }
    };

    private Vector3 rinkPos = new Vector3(); // The position of the rink. Use this to place other TransformableNodes
    private Vector3[] faceOffSpots = {new Vector3(0,0,0), new Vector3(0.25f, 0, 0.09f), new Vector3(0.25f, 0, -0.09f),
        new Vector3(-0.25f, 0, 0.09f), new Vector3(-0.25f, 0, -0.09f)};
    private boolean rinkPlaced = false;
    private boolean stats = false;

    private String[] teams = {"DRW", "TML"}; //, "Sharks", "Boston"}; // Placeholder teams
    private String[] playersArray = {"N. Kronwall", "G. Nyquist", "D. Larkin", "T. Bertuzzi", "J. Franzén",
            "A. Matthews", "W. Nylander", "M. Marner", "J. Gardiner", "J. Tavares"}; // Placeholder players
    //, "E. Karlsson", "J. Thornton",
    //"Z. Chára", "B. Marchand"};
    private Map<String, String> players = new HashMap<String, String>(); // Players and their corresponding teams

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statsToggle = (Button) findViewById(R.id.statsToggle);
        refreshButton = (ImageButton) findViewById(R.id.refresh);
        statsToggle.setVisibility(View.GONE); // Hide until rink is placed
        refreshButton.setVisibility(View.GONE);

        // Clear Firebase when new game starts
        dbShotsRef.removeValue();
        dbEjectionsRef.removeValue();
        dbGoalsRef.removeValue();
        dbFaceOffsRef.removeValue();

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

        game.setParams(getApplicationContext(), "DRW", "TML", "#CE1126", "#003E7E"); // Set starting parameters

        // Assign teams to the players
        int teamCount = 0;
        for (int i=0;i<playersArray.length;i++) {
            players.put(playersArray[i], teams[teamCount]);
            if (i%5 == 4) {
                teamCount += 1;
            }
        }

        ModelRenderable.builder()
                .setSource(this, R.raw.newrink)
                .build()
                .thenAccept(renderable -> hockeyRinkRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        ModelRenderable.builder()
                .setSource(this, R.raw.hockeystick)
                .build()
                .thenAccept(renderable -> hockeyStickRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        ModelRenderable.builder()
                .setSource(this, R.raw.whistle)
                .build()
                .thenAccept(renderable -> whistleRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        ModelRenderable.builder()
                .setSource(this, R.raw.puck)
                .build()
                .thenAccept(renderable -> puckRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        ViewRenderable.builder()
                .setView(this, game.getScoreText())
                .build()
                .thenAccept(renderable -> {
                    scoreBugRenderable = renderable;
                    renderable.setShadowCaster(false);
                });

//        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new com.google.ar.sceneform.rendering.Color(255,40,0))
//                .thenAccept(
//                        material -> {
//                            Vector3 vector3 = new Vector3(0.05f, 0.05f,0.05f);
//                            ejectionModelRenderable = ShapeFactory.makeCube(vector3,
//                                    Vector3.zero(), material);
//                            ejectionModelRenderable.setShadowCaster(false);
//                            ejectionModelRenderable.setShadowReceiver(false);
//                        });

        // arFragment.getArSceneView(). <-- To get access to the ARCore session and more
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {

                    if (hockeyRinkRenderable == null) {
                        return;
                    }
                    if (rinkPlaced) { // Limit to 1 rink placed at a time
                        event(); // Generate an event when detected plane is touched
                        return;
                    } else {

                        // Create the Anchor.
                        Anchor anchor = hitResult.createAnchor();
                        game.setRinkNode(new AnchorNode(anchor));

                        game.getRinkNode().setParent(arFragment.getArSceneView().getScene());

                        // Create the transformable and add it to the anchor.
                        game.setRink(new TransformableNode(arFragment.getTransformationSystem()));
                        game.getRink().setRenderable(hockeyRinkRenderable);

                        // Set correct scale and rotation of rink
                        //hockeyRink.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), -90f));
                        game.getRink().getRotationController().setEnabled(false);
                        game.getRink().getTranslationController().setEnabled(false);
                        game.getRink().getScaleController().setMinScale(0.01f);
                        game.getRink().getScaleController().setMaxScale(2.0f);
                        game.getRink().setLocalScale(new Vector3(0.015f, 0.015f, 0.015f));
                        game.getRink().getScaleController().setEnabled(false);
                        rinkPos = game.getRink().getLocalPosition();
                        //Vector3 temp = new Vector3(rinkPos.x + 0, rinkPos.y + (rinkPos.y+0)/10f, rinkPos.z + 0);
                        //hockeyRink.setLocalPosition(temp);

                        game.getRink().setParent(game.getRinkNode());
                        //hockeyRink.select();

                        arFragment.getArSceneView().getPlaneRenderer().setVisible(false); // Disable plane visualization
                        //arFragment.getArSceneView().getPlaneRenderer().setEnabled(false); // Stop updating planes

                        if (firstHit == null) { // If it's the first time the rink is placed
                            game.setGameTime(System.nanoTime());

                            // Set up listeners here and have them call the corresponding methods
                            dbShotsRef.addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    // This method is called once with the initial value and again
                                    // whenever data at this location is updated.
                                    if (!stats && rinkPlaced) { // If live feed is toggled
                                        long cCount = dataSnapshot.getChildrenCount();
                                        long lCount = 1;
                                        for (DataSnapshot shotSnapshot: dataSnapshot.getChildren()) {
                                            if (lCount >= cCount) {
                                                Shot currShot = shotSnapshot.getValue(Shot.class);
                                                newShot(currShot);
                                            }
                                            lCount += 1;
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    // Failed to read value
                                    Log.w(TAG, "Failed to read value.", error.toException());
                                }
                            });

                            dbEjectionsRef.addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    if (!stats && rinkPlaced) {
                                        long cCount = dataSnapshot.getChildrenCount();
                                        long lCount = 1;
                                        for (DataSnapshot ejectionSnapshot: dataSnapshot.getChildren()) {
                                            if (lCount >= cCount) {
                                                Ejection currEjection = ejectionSnapshot.getValue(Ejection.class);
                                                newEjection(currEjection);
                                            }
                                            lCount += 1;
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    // Failed to read value
                                    Log.w(TAG, "Failed to read value.", error.toException());
                                }
                            });

                            dbGoalsRef.addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    if (!stats && rinkPlaced) {
                                        long cCount = dataSnapshot.getChildrenCount();
                                        long lCount = 1;
                                        for (DataSnapshot goalSnapshot: dataSnapshot.getChildren()) {
                                            if (lCount >= cCount) {
                                                game.setGoal(goalSnapshot.getValue(Goal.class));
                                                newGoal(game.getGoal());
                                            }
                                            lCount += 1;
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    // Failed to read value
                                    Log.w(TAG, "Failed to read value.", error.toException());
                                }
                            });

                            dbFaceOffsRef.addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    if (!stats && rinkPlaced) {
                                        long cCount = dataSnapshot.getChildrenCount();
                                        long lCount = 1;
                                        for (DataSnapshot faceOffSnapshot: dataSnapshot.getChildren()) {
                                            if (lCount >= cCount) {
                                                game.setFaceOff(faceOffSnapshot.getValue(FaceOff.class));
                                                newFaceOff();
                                            }
                                            lCount += 1;
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(DatabaseError error) {
                                    // Failed to read value
                                    Log.w(TAG, "Failed to read value.", error.toException());
                                }
                            });

                            Anchor anchor1 = hitResult.createAnchor();
                            game.setScoreBugNode(new AnchorNode(anchor1));
                            game.getScoreBugNode().setParent(arFragment.getArSceneView().getScene());
                            game.setScoreBug(new TransformableNode(arFragment.getTransformationSystem()));
                            game.getScoreBug().setRenderable(scoreBugRenderable);
                            game.getScoreBug().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.3f, rinkPos.z - 0.35f));
                            game.getScoreBug().setParent(game.getScoreBugNode());

                            handler.postDelayed(eventRunner, 10000);
                            handler.postDelayed(runScoreBug, 0); // Start runnable
                        }

                        rinkPlaced = true;
                        firstHit = hitResult;

                        statsToggle.setVisibility(View.VISIBLE);
                        refreshButton.setVisibility(View.VISIBLE);
                    }
                });
    }

    // Called when a game event occurs
    public void event() {
        // Generate random shooter
        int minPlayer = 0;
        int maxPlayer = playersArray.length - 1;
        int randPlayer = ThreadLocalRandom.current().nextInt(minPlayer, maxPlayer + 1);
        String player = playersArray[randPlayer];
        String team = players.get(player);

        // Generate a random position of the event
        float minZ = -0.13f;
        float maxZ = 0.13f;
        Random r = new Random();
        float rZ = minZ + r.nextFloat() * (maxZ - minZ);
        float minX;
        float maxX;
        if (team == game.getHomeTeam()) {
            minX = 0.05f;
            maxX = 0.3f;
        } else {
            minX = -0.3f;
            maxX = -0.05f;
        }
        r = new Random();
        float rX = minX + r.nextFloat() * (maxX - minX);
        Vector3 pos = new Vector3(rinkPos.x + rX, rinkPos.y + 0, rinkPos.z + rZ);
        //Vector3 pos = new Vector3(rinkPos.x + 0.25f, rinkPos.y + 0, rinkPos.z + 0.10f);

        // Remove the goal view when next event occurs (the game has resumed)
        try {
            game.getGoal().getInfo().getScene().onRemoveChild(game.getGoal().getInfo().getParent());
            game.getGoal().getInfo().setRenderable(null);
            game.getGoal().getNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try { // Remove old face off
            game.getFaceOff().getInfo().getScene().onRemoveChild(game.getFaceOff().getInfo().getParent());
            game.getFaceOff().getInfo().setRenderable(null);
            game.getFaceOff().getNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }

        if (!stats) { // Remove old shots IF live feed is toggled
            Iterator<Shot> i = game.getShotList().iterator();
            while (i.hasNext()) {
                if (i.next().checkTime()) { // Returns true and is deleted IF enough time has passed. Otherwise, returns false and doesn't delete
                    i.remove();
                }
            }
        }
        Iterator<Ejection> j = game.getEjectionList().iterator();
        while (j.hasNext()) {
            if (j.next().checkTime()) { // Returns true and is deleted IF enough time has passed. Otherwise, returns false and doesn't delete
                j.remove();
            }
        }

        int minEvent = 0;
        int maxEvent = 20;
        int randEvent = ThreadLocalRandom.current().nextInt(minEvent,maxEvent + 1);
        //int randEvent = 7;

        // Random event should be generated here
        if (randEvent <= 5) {
            Shot currShot = new Shot(System.nanoTime(), player, team, pos.x, pos.y, pos.z);
            dbShotsRef.push().setValue(currShot);
        } else if (randEvent < 7) {
            Goal currGoal = new Goal(System.nanoTime(), player, team);
            dbGoalsRef.push().setValue(currGoal);
        } else if (randEvent < 8){
            Ejection currEjection = new Ejection(System.nanoTime(), player, team, pos.x, pos.y, pos.z);
            if (team == game.getHomeTeam()) {
                game.setAwayPPMin(game.getAwayPPMin() + 2);
            } else {
                game.setHomePPMin(game.getHomePPMin() + 2);
            }
            dbEjectionsRef.push().setValue(currEjection);
        } else if (randEvent < 9) {
            float shortestDistance = 10;
            Vector3 spot = new Vector3();
            for (int k=0;k<faceOffSpots.length;k++) { // Calculate face off spot
                float distance = Math.abs((pos.x - faceOffSpots[k].x) + (pos.z - faceOffSpots[k].z));
                if (distance < shortestDistance) {
                    shortestDistance = distance;
                    spot = faceOffSpots[k];
                }
            }
            FaceOff currFaceOff = new FaceOff(System.nanoTime(), spot.x, spot.y, spot.z);
            dbFaceOffsRef.push().setValue(currFaceOff);
        }
    }

    /** Called when a shot event occurs */
    public void newShot(Shot currShot) {

        TextView shotText = new TextView(getApplicationContext());

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView
        shotText.setLayoutParams(lp);

        if (stats) { // Stats is toggled, display no text
            shotText.setText("");
        } else {
            shotText.setText("Shot" + "\n" + currShot.player);
        }

        if (currShot.team == game.getHomeTeam()) {
            shotText.setTextColor(Color.parseColor(game.getHomeColor()));
        } else if (currShot.team == game.getAwayTeam()) {
            shotText.setTextColor(Color.parseColor(game.getAwayColor()));
        } else {
            shotText.setTextColor(Color.parseColor("#000000"));
        }

        CompletableFuture<ViewRenderable> shotStage =
                ViewRenderable.builder().setView(this, shotText).build();

        CompletableFuture.allOf(
                shotStage)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                return null;
                            }

                            try {
                                shotInfoRenderable = shotStage.get();

                                shotInfoRenderable.setShadowCaster(false);

                                // Create the Anchor.
                                currShot.setAnchor(firstHit.createAnchor());
                                currShot.setNode(new AnchorNode(currShot.getAnchor()));
                                currShot.getNode().setParent(arFragment.getArSceneView().getScene());

                                // Create the transformable andy and add it to the anchor.
                                currShot.setModel(new TransformableNode(arFragment.getTransformationSystem()));
                                currShot.getModel().setRenderable(hockeyStickRenderable);

                                currShot.setInfo(new TransformableNode(arFragment.getTransformationSystem()));
                                currShot.getInfo().setRenderable(shotInfoRenderable);

                                currShot.getModel().getScaleController().setMinScale(0.01f);
                                currShot.getModel().getScaleController().setMaxScale(2.0f);
                                currShot.getModel().setLocalScale(new Vector3(0.06f, 0.06f, 0.06f));
                                currShot.getModel().getScaleController().setEnabled(false);
                                currShot.getModel().getRotationController().setEnabled(false);
                                currShot.getModel().setLocalPosition(new Vector3(currShot.xPos, currShot.yPos + 0.02f, currShot.zPos));
                                currShot.getInfo().setLocalPosition(new Vector3(currShot.xPos, currShot.yPos + 0.1f, currShot.zPos));

                                currShot.getModel().setParent(currShot.getNode());
                                currShot.getInfo().setParent(currShot.getNode());

                                game.getShotList().add(currShot);
                            } catch (InterruptedException | ExecutionException ex) {
                            }

                            return null;
                        });
    }

    // Called when there is a goal
    public void newGoal(Goal currGoal) {

        if (currGoal.team == game.getHomeTeam()) {
            game.setHomeScore(game.getHomeScore() + 1);
        } else if (currGoal.team == game.getAwayTeam()) {
            game.setAwayScore(game.getAwayScore() + 1);
        }

        TextView goalText = new TextView(getApplicationContext());

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView
        goalText.setLayoutParams(lp);

        String temp = "GOAL " + currGoal.team + "\n" + currGoal.player + "\n" + String.valueOf(game.getHomeScore()) + " - " + String.valueOf(game.getAwayScore());
        goalText.setText(temp);

        // Set a text color for TextView text
        Vector3 goalTextPos;
        if (currGoal.team == game.getHomeTeam()) {
            goalText.setTextColor(Color.parseColor(game.getHomeColor()));
            goalTextPos = new Vector3(rinkPos.x + 0.3f, rinkPos.y + 0.1f, rinkPos.z);
        } else if (currGoal.team == game.getAwayTeam()) {
            goalText.setTextColor(Color.parseColor(game.getAwayColor()));
            goalTextPos = new Vector3(rinkPos.x - 0.3f, rinkPos.y + 0.1f, rinkPos.z);
        } else {
            goalText.setTextColor(Color.parseColor("#000000"));
            goalTextPos = new Vector3(rinkPos.x, rinkPos.y + 0.1f, rinkPos.z);
        }

        CompletableFuture<ViewRenderable> goalStage =
                ViewRenderable.builder().setView(this, goalText).build();

        CompletableFuture.allOf(
                goalStage)
                .handle(
                        (notUsed, throwable) -> {

                            if (throwable != null) {
                                return null;
                            }

                            try {
                                goalInfoRenderable = goalStage.get();

                                goalInfoRenderable.setShadowCaster(false);

                                // Create the Anchor.
                                Anchor anchor = firstHit.createAnchor();
                                game.getGoal().setNode(new AnchorNode(anchor));
                                game.getGoal().getNode().setParent(arFragment.getArSceneView().getScene());

                                game.getGoal().setInfo(new TransformableNode(arFragment.getTransformationSystem()));
                                game.getGoal().getInfo().setRenderable(goalInfoRenderable);

                                game.getGoal().getInfo().setLocalPosition(goalTextPos);

                                game.getGoal().getInfo().setParent(game.getGoal().getNode());
                            } catch (InterruptedException | ExecutionException ex) {
                            }

                            return null;
                        });
    }

    public void newEjection(Ejection currEjection) { // Called when a player is ejected from play

        TextView ejectionText = new TextView(getApplicationContext());

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView
        ejectionText.setLayoutParams(lp);

        ejectionText.setText(currEjection.violation + "\n" + currEjection.player);

        if (currEjection.team == game.getHomeTeam()) {
            ejectionText.setTextColor(Color.parseColor(game.getHomeColor()));
        } else if (currEjection.team == game.getAwayTeam()) {
            ejectionText.setTextColor(Color.parseColor(game.getAwayColor()));
        } else {
            ejectionText.setTextColor(Color.parseColor("#000000"));
        }

        CompletableFuture<ViewRenderable> ejectionStage =
                ViewRenderable.builder().setView(this, ejectionText).build();

        CompletableFuture.allOf(
                ejectionStage)
                .handle(
                        (notUsed, throwable) -> {

                            if (throwable != null) {
                                return null;
                            }

                            try {
                                ejectionInfoRenderable = ejectionStage.get();

                                ejectionInfoRenderable.setShadowCaster(false);

                                // Create the Anchor.
                                currEjection.setAnchor(firstHit.createAnchor());
                                currEjection.setNode(new AnchorNode(currEjection.getAnchor()));
                                currEjection.getNode().setParent(arFragment.getArSceneView().getScene());

                                // Create the transformable and add it to the anchor.
                                currEjection.setModel(new TransformableNode(arFragment.getTransformationSystem()));
                                currEjection.getModel().setRenderable(whistleRenderable);
                                currEjection.getModel().getScaleController().setMinScale(0.01f);
                                currEjection.getModel().getScaleController().setMaxScale(2.0f);
                                currEjection.getModel().setLocalScale(new Vector3(0.03f, 0.03f, 0.03f));
                                currEjection.getModel().setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 90));
                                currEjection.getModel().getRotationController().setEnabled(false);
                                currEjection.getModel().getScaleController().setEnabled(false);

                                currEjection.setInfo(new TransformableNode(arFragment.getTransformationSystem()));
                                currEjection.getInfo().setRenderable(ejectionInfoRenderable);

                                currEjection.getModel().setLocalPosition(new Vector3(currEjection.xPos, currEjection.yPos + 0.02f, currEjection.zPos));
                                currEjection.getInfo().setLocalPosition(new Vector3(currEjection.xPos, currEjection.yPos + 0.1f, currEjection.zPos));

                                currEjection.getModel().setParent(currEjection.getNode());
                                currEjection.getInfo().setParent(currEjection.getNode());

                                game.getEjectionList().add(currEjection);

                                // Check if there are any current PPs. If not, create PP
                                if (currEjection.team == game.getHomeTeam()) {
                                    if (!game.isAwayPPBool()) {
                                        game.setAwayPPStart(currEjection.getTime());
                                        handler.postDelayed(ppAwayRun, 0);
                                        game.setAwayPPBool(true);
                                    }
                                } else if (currEjection.team == game.getAwayTeam()) {
                                    if (!game.isHomePPBool()) {
                                        game.setHomePPStart(currEjection.getTime());
                                        handler.postDelayed(ppHomeRun, 0);
                                        game.setHomePPBool(true);
                                    }
                                }
                            } catch (InterruptedException | ExecutionException ex) {
                            }

                            return null;
                        });
    }

    // Called when there is a face off
    public void newFaceOff() {

        TextView faceOffText = new TextView(getApplicationContext());

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView
        faceOffText.setLayoutParams(lp);

        String temp = "Face Off";
        faceOffText.setText(temp);
        faceOffText.setTextColor(Color.parseColor("#000000"));

        CompletableFuture<ViewRenderable> faceOffStage =
                ViewRenderable.builder().setView(this, faceOffText).build();

        CompletableFuture.allOf(
                faceOffStage)
                .handle(
                        (notUsed, throwable) -> {
                            if (throwable != null) {
                                return null;
                            }

                            try {
                                faceOffRenderable = faceOffStage.get();

                                faceOffRenderable.setShadowCaster(false);

                                // Create the Anchor.
                                Anchor anchor = firstHit.createAnchor();
                                game.getFaceOff().setNode(new AnchorNode(anchor));
                                game.getFaceOff().getNode().setParent(arFragment.getArSceneView().getScene());

                                // Create the transformable and add it to the anchor.
                                game.getFaceOff().setModel(new TransformableNode(arFragment.getTransformationSystem()));
                                game.getFaceOff().getModel().setRenderable(puckRenderable);
                                game.getFaceOff().getModel().getScaleController().setMinScale(0.01f);
                                game.getFaceOff().getModel().getScaleController().setMaxScale(2.0f);
                                game.getFaceOff().getModel().setLocalScale(new Vector3(0.018f, 0.018f, 0.018f));
                                //game.getFaceOff().getModel().setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 90));
                                game.getFaceOff().getModel().getRotationController().setEnabled(false);
                                game.getFaceOff().getModel().getScaleController().setEnabled(false);

                                game.getFaceOff().setInfo(new TransformableNode(arFragment.getTransformationSystem()));
                                game.getFaceOff().getInfo().setRenderable(faceOffRenderable);

                                game.getFaceOff().getInfo().setLocalPosition(new Vector3(game.getFaceOff().xPos, game.getFaceOff().yPos + 0.1f, game.getFaceOff().zPos));
                                game.getFaceOff().getModel().setLocalPosition(new Vector3(game.getFaceOff().xPos, game.getFaceOff().yPos, game.getFaceOff().zPos));

                                game.getFaceOff().getModel().setParent(game.getFaceOff().getNode());
                                game.getFaceOff().getInfo().setParent(game.getFaceOff().getNode());
                            } catch (InterruptedException | ExecutionException ex) {
                            }

                            return null;
                        });
    }

    // Toggle the statistics
    public void statistics(View view) {

        // Remove all current rendered models when toggling on/off
        try {
            game.getGoal().getInfo().getScene().onRemoveChild(game.getGoal().getInfo().getParent());
            game.getGoal().getInfo().setRenderable(null);
            game.getGoal().getNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try {
            game.getFaceOff().getInfo().getScene().onRemoveChild(game.getFaceOff().getInfo().getParent());
            game.getFaceOff().getInfo().setRenderable(null);
            game.getFaceOff().getNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try {
            game.getScoreBug().getScene().onRemoveChild(game.getScoreBug().getParent());
            game.getScoreBug().setRenderable(null);
            game.getScoreBugNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try {
            game.getHomePP().getScene().onRemoveChild(game.getHomePP().getParent());
            game.getHomePP().setRenderable(null);
            game.getHomePPNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try {
            game.getAwayPP().getScene().onRemoveChild(game.getAwayPP().getParent());
            game.getAwayPP().setRenderable(null);
            game.getAwayPPNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        Iterator<Shot> i = game.getShotList().iterator();
        while (i.hasNext()) {
            i.next().deleteRender();
            i.remove();
        }
        Iterator<Ejection> j = game.getEjectionList().iterator();
        while (j.hasNext()) {
            j.next().deleteRender();
            j.remove();
        }

        if (!stats) { // Live feed is toggled, switch to stats
            stats = true; // Stats is now toggled

            dbShotsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) { // This fires directly and listener is removed after(?)
                    int homeShots = 0;
                    int awayShots = 0;
                    for (DataSnapshot shotSnapshot: dataSnapshot.getChildren()) {
                        Shot currShot = shotSnapshot.getValue(Shot.class);
                        newShot(currShot);

                        if (currShot.team == game.getHomeTeam()) {
                            homeShots += 1;
                        } else {
                            awayShots += 1;
                        }
                    }

                    TextView statsText = new TextView(getApplicationContext());
                    statsText.setGravity(Gravity.CENTER);
                    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                            RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView
                    statsText.setLayoutParams(lp);

                    String text = "GOALS" + "\n" + "SHOTS" + "\n" + "GOAL %" + "\n" + "PP MIN";
                    statsText.setTextColor(Color.parseColor("#ffffff"));
                    statsText.setText(text);

                    TextView statsTextL = new TextView(getApplicationContext());
                    statsTextL.setGravity(Gravity.CENTER);
                    statsTextL.setLayoutParams(lp);

                    TextView statsTextR = new TextView(getApplicationContext());
                    statsTextL.setGravity(Gravity.CENTER);
                    statsTextL.setLayoutParams(lp);

                    try { // If shots == 0
                        String textL = "<font color="+game.getHomeColor()+">"+ game.getHomeScore() + "<br />" +
                                Integer.toString(homeShots) + "<br />" +
                                Integer.toString((game.getHomeScore()*100)/homeShots) + "<br />" +
                                Integer.toString(game.getHomePPMin()) + "</font>";
                        statsTextL.setText(Html.fromHtml(textL));
                    } catch (ArithmeticException e) { // If shots = 0
                        String textL = "<font color="+game.getHomeColor()+">"+ game.getHomeScore() + "<br />" +
                                Integer.toString(homeShots) + "<br />" +
                                Integer.toString((game.getHomeScore()*100)/1) + "<br />" +
                                Integer.toString(game.getHomePPMin()) + "</font>";
                        statsTextL.setText(Html.fromHtml(textL));
                    }
                    try {
                        String textR = "<font color="+game.getAwayColor()+">"+ game.getAwayScore() + "<br />" +
                                Integer.toString(awayShots) + "<br />" +
                                Integer.toString((game.getAwayScore()*100)/awayShots) + "<br />" +
                                Integer.toString(game.getAwayPPMin()) + "</font>";
                        statsTextR.setText(Html.fromHtml(textR));
                    } catch (ArithmeticException e) { // If shots = 0
                        String textR = "<font color="+game.getAwayColor()+">"+ game.getAwayScore() + "<br />" +
                                Integer.toString(awayShots) + "<br />" +
                                Integer.toString((game.getAwayScore()*100)/1) + "<br />" +
                                Integer.toString(game.getAwayPPMin()) + "</font>";
                        statsTextR.setText(Html.fromHtml(textR));
                    }

                    CompletableFuture<ViewRenderable> statsStage =
                            ViewRenderable.builder().setView(getApplicationContext(), statsText).build();
                    CompletableFuture<ViewRenderable> statsLStage =
                            ViewRenderable.builder().setView(getApplicationContext(), statsTextL).build();
                    CompletableFuture<ViewRenderable> statsRStage =
                            ViewRenderable.builder().setView(getApplicationContext(), statsTextR).build();

                    CompletableFuture.allOf(
                            statsStage,
                            statsLStage,
                            statsRStage)
                            .handle(
                                    (notUsed, throwable) -> {
                                        if (throwable != null) {
                                            return null;
                                        }

                                        try {
                                            statsRenderable = statsStage.get();
                                            statsLRenderable = statsLStage.get();
                                            statsRRenderable = statsRStage.get();

                                            statsRenderable.setShadowCaster(false);
                                            statsLRenderable.setShadowCaster(false);
                                            statsRRenderable.setShadowCaster(false);

                                            Anchor anchor = firstHit.createAnchor();
                                            game.setStatsNode(new AnchorNode(anchor));
                                            game.getStatsNode().setParent(arFragment.getArSceneView().getScene());

                                            game.setStats(new TransformableNode(arFragment.getTransformationSystem()));
                                            game.setStatsL(new TransformableNode(arFragment.getTransformationSystem()));
                                            game.setStatsR(new TransformableNode(arFragment.getTransformationSystem()));
                                            game.getStats().setRenderable(statsRenderable);
                                            game.getStatsL().setRenderable(statsLRenderable);
                                            game.getStatsR().setRenderable(statsRRenderable);

                                            game.getStats().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.2f, rinkPos.z - 0.35f));
                                            game.getStatsL().setLocalPosition(new Vector3(rinkPos.x - 0.2f, rinkPos.y + 0.2f, rinkPos.z - 0.35f));
                                            game.getStatsR().setLocalPosition(new Vector3(rinkPos.x + 0.2f, rinkPos.y + 0.2f, rinkPos.z - 0.35f));

                                            game.getStats().setParent(game.getStatsNode());
                                            game.getStatsL().setParent(game.getStatsNode());
                                            game.getStatsR().setParent(game.getStatsNode());
                                        } catch (InterruptedException | ExecutionException ex) {
                                        }

                                        return null;
                                    });
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    // Failed to read value
                    Log.w(TAG, "Failed to read value.", databaseError.toException());
                }
            });

        } else {
            stats = false;

            try { // Remove stats
                game.getStats().getScene().onRemoveChild(game.getStats().getParent());
                game.getStatsL().getScene().onRemoveChild(game.getStatsL().getParent());
                game.getStatsR().getScene().onRemoveChild(game.getStatsR().getParent());
            } catch (NullPointerException e) {
            } finally {
                game.getStats().setRenderable(null);
                game.getStatsL().setRenderable(null);
                game.getStatsNode().getAnchor().detach();
            }
        }
    }

    // Removes the rink and lets the user place it again in a new location
    public void refresh(View view) {
        rinkPlaced = false;

        // Remove all elements on screen and all renderables
        statsToggle.setVisibility(View.GONE);
        refreshButton.setVisibility(View.GONE);

        try {
            game.getGoal().getInfo().getScene().onRemoveChild(game.getGoal().getInfo().getParent());
            game.getGoal().getInfo().setRenderable(null);
            game.getGoal().getNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try {
            game.getFaceOff().getInfo().getScene().onRemoveChild(game.getFaceOff().getInfo().getParent());
            game.getFaceOff().getInfo().setRenderable(null);
            game.getFaceOff().getNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try {
            game.getScoreBug().getScene().onRemoveChild(game.getScoreBug().getParent());
            game.getScoreBug().setRenderable(null);
            game.getScoreBugNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try {
            game.getHomePP().getScene().onRemoveChild(game.getHomePP().getParent());
            game.getHomePP().setRenderable(null);
            game.getHomePPNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        try { // Clear PP anchors
            game.getAwayPP().getScene().onRemoveChild(game.getAwayPP().getParent());
            game.getAwayPP().setRenderable(null);
            game.getAwayPPNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }
        Iterator<Shot> i = game.getShotList().iterator();
        while (i.hasNext()) {
            i.next().deleteRender();
            i.remove();
        }
        Iterator<Ejection> j = game.getEjectionList().iterator();
        while (j.hasNext()) {
            j.next().deleteRender();
            j.remove();
        }
        if (stats) {
            try { // Remove stats
                game.getStats().getScene().onRemoveChild(game.getStats().getParent());
                game.getStatsL().getScene().onRemoveChild(game.getStatsL().getParent());
                game.getStatsR().getScene().onRemoveChild(game.getStatsR().getParent());
            } catch (NullPointerException e) {
            } finally {
                game.getStats().setRenderable(null);
                game.getStatsL().setRenderable(null);
                game.getStatsNode().getAnchor().detach();
            }
        }
        try {
            game.getRink().getScene().onRemoveChild(game.getRink().getParent());
            game.getRink().setRenderable(null);
            game.getRinkNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }

        arFragment.getArSceneView().getPlaneRenderer().setVisible(true);
    }
}
