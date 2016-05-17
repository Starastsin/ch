
package internetofthings;

import com.jme3.asset.AssetManager;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.Savable;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import engine.sprites.SpriteInfo;
import extra.SkyRay;
import gameobject.worldobject.Grass;
import gameobject.worldobject.Tree;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import thomland.Thomland;

/**
 * Internet of Thing is architecture of controlling and manipulating every
 * entity, object, player, npc - links of its contains in 1 class to provide
 * synchronization between threads, clients and server
 * @author Anton Starastsin
 * 
 */
public class InternetOfThings implements Savable{   
    private Node internetWorld;
    //private ConcurrentHashMap<Long, Thing> things;
    private HTreeMap<Long, Thing> things;
    private DB db;
    private ScheduledThreadPoolExecutor executor;
    private LinkedBlockingQueue<Command> commandQueue;
    private LinkedBlockingQueue<Command> updateQueue;
    private LinkedBlockingQueue<Command> handlerQueue;
    private LinkedBlockingQueue<Command> generatorQueue;
    private final int POOL_SIZE = 4;
    private State state;
    private transient AssetManager assetManager;
    
    private static final int THREAD_SLEEP_TIME = 1;
    
    /**
     * Debug variables
     */
    private int treeThreshold = 40;
    private float minimumSpawnHeight = -30f;
    
    public enum State{
        RUNNING, STOPPED, SUSPENDED, NOT_INITIALIZED
    }
    
    public enum CommandType{
        /**
         * Add thing to the Internet of Things. If object is not the 
         * instance of Thing but Spatial, it also will be added, but lose 
         * benefits of Things and under several conditionals.
         * 1st object is the thing, or spatial.
         */
        ADD_THING, 
        /**
         * Delete thing or spatial from the IOT. 
         * 1st object is the id or name.
         */
        DELETE_THING,
        /**
         * Add things to the Internet of Things. If object is not the 
         * instance of List contains things but List contains 
         * Spatials, it also will be added, but lose 
         * benefits of Things and under several conditionals.
         * 1st object is the list of things or spatials.
         */
        ADD_THINGS,
        /**
         * Delete list of things or spatials from the IOT. 
         * 1st object is the list of id's or names.
         */
        DELETE_THINGS,
        /**
         * Get list of spatials and sprites to render 
         * 1st object is a Vector3f - location of camera
         * 2nd object is a Float - radius of rendering
         * 3d object is a Float - radius of sprite and nonThing rendering
         * 4) HashSet of Spatials(can be null)
         * 5) HashSet of SpriteInfo
         * 
         * InternetResult returns:
         * 1) HashSet of Spatials to attach
         * 2) HashSet of Spatials to detach
         * 3) HashSet of SpriteInfo to attach
         * 4) HashSet of SpriteInfo to detach
         */
        GET_LIST_TO_RENDER,
        /**
         * Execute enviroment generating(trees, flowers, landscape, rocks)
         * 1) GenerateOptions
         * 
         * InternetResult returns:
         * nothing
         */
        GENERATE_ENVIROMENT
    }
    
    public InternetOfThings(){
        state = State.NOT_INITIALIZED;
    }
    
    /**
     * Initialize Internet of Things
     */
    public void initialize(){
        state = State.SUSPENDED;
        commandQueue = new LinkedBlockingQueue<>();
        updateQueue = new LinkedBlockingQueue<>();
        handlerQueue = new LinkedBlockingQueue<>();
        generatorQueue = new LinkedBlockingQueue<>();
        executor = new ScheduledThreadPoolExecutor(POOL_SIZE);
        executor.submit(CommandOperatorLoop);
        executor.submit(ThingsUpdateLoop);
        executor.submit(InternetHandlerLoop);
        executor.submit(GeneratorLoop);
        //things = new ConcurrentHashMap<>();
        internetWorld = new Node("internet world");
        //
        db = DBMaker.heapDB().transactionEnable().make();
    
        things = db.hashMap("map", org.mapdb.Serializer.LONG, org.mapdb.Serializer.JAVA).createOrOpen();
        state = State.RUNNING;
    }
    
    /**
     * Use this method when closing server or/and application.  
     */
    public void shutdown(){
        state = State.STOPPED;
        executor.shutdown();
        
        db.close();
    }

    @Override
    public void write(JmeExporter ex) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
    
    /**
     * Send command to Internet Of Things. For debug puproses and flexibility
     * every command use this interface and common object[] commands.
     * Read commandType to get required arguments 
     * @param commandType
     * @param arguments depends on commandType. Read description of selected
     * commandType
     * @return InternetResult to check status of command whether it executed or
     * failed
     */
    public InternetResult submitCommand(CommandType commandType, Object[] arguments){
        InternetResult internetResult = new InternetResult();
        Command command = new Command(commandType, arguments, internetResult);
        try {
            commandQueue.put(command);
        } catch (InterruptedException ex) {
            System.err.println
            ("[INTERNET OF THINGS] Interrupted exception while submitting command");
        }
        return internetResult;
    }
    
    /**
     * Execute or transfer commands in the queue.
     */
    private Callable<Void> CommandOperatorLoop = new Callable<Void>(){
        private Command currentCommand;
        
        @Override
        public Void call(){
            Thread.currentThread().setName("CommandOperatorLoop");
            while(state != State.STOPPED){
                if (state == State.RUNNING){
                    try {
                        if (!commandQueue.isEmpty()){
                            currentCommand = commandQueue.take();
                            transferCommand(currentCommand);
                        }
                        Thread.sleep(THREAD_SLEEP_TIME);
                    } catch (InterruptedException ex) {
                        System.err.println
                            ("[InternetOfThings] CommandExecutorLoop"
                                    + " interrupted:" + ex);
                    } 
                }
            }
            return null;
        }
        
        /**
         * Retranslate command to other threads depending on it type
         * @param command retranslated command
         */
        private void transferCommand(Command command){
            try {
                switch (command.getCommandType()){
                    case ADD_THING: updateQueue.put(command); break;
                    case ADD_THINGS: updateQueue.put(command); break;
                    case DELETE_THING: updateQueue.put(command); break;
                    case DELETE_THINGS: updateQueue.put(command); break;
                    case GET_LIST_TO_RENDER: handlerQueue.put(command); break;
                    case GENERATE_ENVIROMENT: generatorQueue.put(command); break;
                    default: 
                        throw new UnsupportedOperationException
                        ("[Internet of things] command " +
                        command.getCommandType().toString() + " is unsupported yet"); 
                }
            } catch (InterruptedException ex){
                System.err.println
                    ("[INTERNET OF THING] Interrupted exception while "
                            + "transfering command in CommandOperatorLoop" + ex);
            }
        }
    };
    
    
    
    /**
     * Process modifying, updating, deleting things
     */
    private Callable<Void> ThingsUpdateLoop = new Callable<Void>(){
        private Command currentCommand;
        
        @Override
        public Void call(){
            Thread.currentThread().setName("ThingsUpdateLoop");
            while(state != State.STOPPED){
                if (state == State.RUNNING){
                    try {
                        if (!updateQueue.isEmpty()){
                            currentCommand = updateQueue.take();
                            executeCommand(currentCommand);
                        }
                        Thread.sleep(THREAD_SLEEP_TIME);
                    } catch (InterruptedException ex) {
                        System.err.println
                            ("[INTERNET OF THINGS] thingsUpdateLoop interrupted:" + ex);
                    } 
                }
            }
            return null;
        }
        
        /**
         * Execute command
         * @param command 
         */
        private void executeCommand(Command command){
            switch (command.commandType){
                case ADD_THING: addThing(command); break;
            }
            
        }
        
        private void addThing(Command command){
            try{
                Object operatedObject = command.getArguments()[0];
                Thing operatedThing = null;
                Spatial operatedSpatial = null;
                if (operatedObject instanceof Thing){
                    operatedThing = (Thing) operatedObject;
                    if (operatedThing.isCompressed()){
                        operatedThing.recreate();
                    }
                    things.put(operatedThing.getID(), operatedThing);
                    internetWorld.attachChild(operatedThing.getSpatial());
                } else {
                    operatedSpatial = (Spatial) operatedObject;
                    internetWorld.attachChild(operatedSpatial);
                }
                currentCommand.getInternetResult().
                        finish(InternetResult.ResultStatus.COMPLETED, 
                               "Successfully added", 
                               new Object[]{operatedObject});
            
            } catch (Exception ex){
                System.err.println("[IOT] Update loop exception: " + ex);
                ex.printStackTrace();
            }
        }
        
    };
    
    
    /**
     * Process different operations based on things and Internet. Like 
     * synchronizing, retrieving info about things, providing set of things
     * to render
     */
    private Callable<Void> InternetHandlerLoop = new Callable<Void>(){
        
        // For get list to render to avoid recreatinc each time
        private Command currentCommand;
        private HashSet<Spatial> spatialsToRender = new HashSet<>();
        private HashSet<SpriteInfo> spriteInfoToRender = new HashSet<>();
        private HashSet<Spatial> spatialsToAttach = new HashSet<>();
        private HashSet<Spatial> spatialsToDetach = new HashSet<>();
        private HashSet<SpriteInfo> spriteInfoToAttach = new HashSet<>();
        private HashSet<SpriteInfo> spriteInfoToDetach = new HashSet<>(); 
        private HashSet<SpriteInfo> spriteInfoToDetachAdditional = new HashSet<>();
        
        @Override
        public Void call(){
            Thread.currentThread().setName("InternetHandlerLoop");
            while(state != State.STOPPED){
                if (state == State.RUNNING){
                    try {
                        if (!handlerQueue.isEmpty()){
                            currentCommand = handlerQueue.take();
                            executeCommand(currentCommand);
                        }
                        Thread.sleep(THREAD_SLEEP_TIME);
                    } catch (InterruptedException ex) {
                        System.err.println
                            ("[InternetOfThings] CommandExecutorLoop"
                                    + " interrupted:" + ex);
                    } catch (Exception ex){
                        System.err.println("[INTERNET OF THINGS] InternetHandlerLoop" + ex);
                        ex.printStackTrace();
                    }
                }
            }
            return null;
        }
        
        /**
         * Execute command
         * @param command 
         */
        private void executeCommand(Command command){
            switch (command.getCommandType()){
                case GET_LIST_TO_RENDER: getListToRender(command);  break;
            }
            
        }
        
        /**
         * Get lists of visible spatials and sprites 
         * @param command 
         */
        private void getListToRender(Command command){
            Vector3f cameraLocation = (Vector3f) command.getArguments()[0];
            float renderDistance = (Float) command.getArguments()[1];
            float farDistance = (Float) command.getArguments()[2];
            HashSet<Spatial> spatials = (HashSet<Spatial>) command.getArguments()[3];
            HashSet<SpriteInfo> spriteInfos = (HashSet<SpriteInfo>) command.getArguments()[4];
            spatialsToRender.clear();
            spriteInfoToRender.clear();
            
            /**
             * Difference between Spatial and SpriteInfo is Spatial should be
             * already ready before attaching. But sprite creates only while
             * attaching and destroyes while detaching. So keep in mind
             * if there are spriteinfo in both attach and detach lists. This 
             * mean it should update his position(be deleted and added in
             * another place). And that's all.
             */
            spatialsToAttach.clear();
            spatialsToDetach.clear();
            spriteInfoToAttach.clear();
            spriteInfoToDetach.clear();
            for (Thing thing : things.getValues()){
                //thing.initialize(thing.getID(), thing.getClassID(), thing.getType(), assetManager);
                //thing.setLocation(thing.getLocation());
                if (thing.getLocation().distance(cameraLocation) < renderDistance){
                    if (thing.getRenderState() == thing.getState() 
                            && thing.getRenderedSpatial() != null ){
                        spatialsToRender.add(thing.getRenderedSpatial());
                    } else if (thing.getRenderedSpatial() == null){
                        thing.render();
                        spatialsToRender.add(thing.getRenderedSpatial());
                    } else if (thing.getRenderState() != thing.getState()){
                        thing.render();
                        spatialsToRender.add(thing.getRenderedSpatial());
                    }
                } else{
                    if (thing.getRenderedSpatial() != null){
                        
                    }
                    
                    if (thing instanceof InternetSprite){ //thing instanceof InternetSprite
                        if (((InternetSprite) thing).isSupportSprite()){
                            SpriteInfo spriteInfo = ((InternetSprite) thing).spriteInfo();
                            spriteInfoToRender.add(spriteInfo);
                            if (spriteInfo.getRenderState() == thing.getRenderState() 
                                    && spriteInfo.getConnectedSprite() != null ){
                                
                            } else if (spriteInfo.getConnectedSprite() == null){
                                spriteInfoToAttach.add(spriteInfo);
                            } else if (thing.getRenderState() != spriteInfo.getRenderState()){
                                spriteInfoToDetach.add(spriteInfo);
                                spriteInfoToAttach.add(spriteInfo);
                                spriteInfo.setRenderState(thing.getRenderState());
                            }
                        }
                    }
                }
            }
                spatialsToDetach.addAll(spatials);
                spatialsToDetach.removeAll(spatialsToRender);
                spatialsToRender.removeAll(spatials);
                spatialsToAttach.addAll(spatialsToRender);

                spriteInfoToDetachAdditional.clear();
                spriteInfoToDetachAdditional.addAll(spriteInfos);
                spriteInfoToDetachAdditional.removeAll(spriteInfoToRender);
                spriteInfoToDetach.addAll(spriteInfoToDetachAdditional);
                command.getInternetResult().finish(InternetResult.ResultStatus.COMPLETED, 
                        "", new Object[]{spatialsToAttach, 
                                         spatialsToDetach,
                                         spriteInfoToAttach,
                                         spriteInfoToDetach});
                
                
        }

    };
    
    
    
    /**
     * Process generating and regenerating(control population) enviroment
     */
    private Callable<Void> GeneratorLoop = new Callable<Void>(){
        private Command currentCommand;
        
        @Override
        public Void call(){
            Thread.currentThread().setName("GeneratorLoop");
            while(state != State.STOPPED){
                if (state == State.RUNNING){
                    try {
                        if (!generatorQueue.isEmpty()){
                            currentCommand = generatorQueue.take();
                            executeCommand(currentCommand);
                        }
                        Thread.sleep(THREAD_SLEEP_TIME);
                    } catch (InterruptedException ex) {
                        System.err.println
                            ("[INTERNET OF THINGS] GeneratorLoop interrupted:" + ex);
                    } 
                }
            }
            return null;
        }
        
        /**
         * Execute command
         * @param command 
         */
        private void executeCommand(Command command){
            switch (command.commandType){
                case GENERATE_ENVIROMENT: generate(command); break;
            }
            
        }
        
        private void generate(Command command){
            GenerateOptions generateOptions = (GenerateOptions)command.getArguments()[0];
            Node temp = Thomland.thomClient.getRootNode().clone(true);
            SkyRay skyRay = new SkyRay(
                    generateOptions.getWidth(),
                    generateOptions.getHeight(), 
                    temp,
                    generateOptions.getDensity());
            System.out.println("Casting spawners");
            ArrayList<Vector3f> spawners = skyRay.cast(generateOptions.getSurfaceName());
            int index = 0;
            long id = 0;
            for (Vector3f spawner:spawners){
                if (spawner.y > minimumSpawnHeight){
                    index++;
                    id ++;
                    if (index < treeThreshold ){
                        try{
                        Grass grass = new Grass();
                        grass.initialize(id, 1, (byte)1, assetManager );
                        grass.setLocation(spawner);
                            submitCommand(
                                    InternetOfThings.CommandType.ADD_THING,
                                    new Object[]{grass});
                        } catch (Exception ex){
                            System.err.println("[IOT] Spawn grass exception:" + ex);
                        }
                    } else{
                        index = 0;
                        try{
                        Tree tree = new Tree();
                        tree.initialize(id, 2, (byte)1, assetManager );
                        tree.setLocation(spawner);
                            submitCommand(
                                    InternetOfThings.CommandType.ADD_THING,
                                    new Object[]{tree});
                        } catch (Exception ex){
                            System.err.println("[IOT] Spawn tree exception: " + ex);
                        }
                    }
                }
            }
            temp = null;
            System.out.println(spawners.size() + " SPAWNER SIZE");
            command.getInternetResult().finish
                (InternetResult.ResultStatus.COMPLETED, "", null);
        }
        
        
    };
    
    
    
    /**
     * Command to Internet of Things comes from outer space and executed by
     * particular loop
     */
    private class Command{
        private CommandType commandType;
        private Object[] arguments;
        private InternetResult internetResult;
        
        public Command(CommandType commandType, Object[] arguments, InternetResult internetResult){
            this.commandType = commandType;
            this.arguments = arguments;
            this.internetResult = internetResult;
        } 

        public CommandType getCommandType() {
            return commandType;
        }

        public Object[] getArguments() {
            return arguments;
        }

        public InternetResult getInternetResult() {
            return internetResult;
        }
        
    }
    
    public State getState() {
        return state;
    }
    
    public AssetManager getAssetManager() {
        return assetManager;
    }

    public void setAssetManager(AssetManager assetManager) {
        this.assetManager = assetManager;
    }
    
}
