
package internetofthings;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import engine.sprites.Sprite;
import java.io.Serializable;

/**
 * Thing is the object could be fully connected with Internet of Things system
 * @author Anton
 */
public interface Thing extends Serializable{
    
    /**
     * 
     * @return id that represents particular class in particular Internet Of
     * Things
     */
    public int getClassID();
    
    public long getID();
    
    /**
     * State is the number of modifications modify thing physical model, location
     * and so on. It starts from 1 and required for synchonizations to indicate
     * when information about the thing should be resended from server
     * @return state
     */
    public int getState();
    
    /**
     * State is the number of modifications modify thing physical model, location
     * and so on. It starts from 1 and required for synchonizations to indicate
     * when information about the thing should be resended from server
     * @param state
     */
    public void setState(int state);
    
    /**
     * Get state when this thing was rendered. Used to inform that current
     * rendered spatial could be obsolete
     * @return state
     */
    public int getRenderState();
    
    public Spatial getSpatial();
    
    public Spatial getRenderedSpatial();
   
    
    /**
     * Create rendered spatial from spatial. And if rendered spatial != null
     * it will be replaced
     */
    public void render();
    
    public void destroyRenderedSpatial();
    
    public void setLocation(Vector3f location);
    
    public void setLocation(float x, float y, float z);
    
    public Vector3f getLocation();
    
    /**
     * Same things could have different color, details of shape. In other words,
     * different types. If there is only 1 type then it will always has number 1
     * @return id of type of Thing. Starts from 1
     * If id == 0 or null, it usually means type one(but better it should be fixed!)
     */
    public byte getType();
    
    /**
     * 
     * @return how much thing has types. From 1 to return value.
     * 0 or null means only 1 type.
     */
    public byte getTypeRange();
    
    public boolean hasCollision();
    
    /**
     * Get custom data about thing. It should be savable and serializable
     * @param <T>
     * @param key
     * @return data
     */
    public <T> T getData(String key);
    
    /**
     * Set data connected with key
     * @param key name of the data
     * @param data data-object
     */
    public void setData(String key, Object data);
    
    
    
    /**
     * This is maximumim distance of interaction between thing and something
     * that cause action. If distance between them > interactionRadius, then
     * nothing happens. For anti-cheat and synchonize purposes
     * @return max distance of interaction
     */
    public float getInteractionRadius();
    
    /**
     * 
     * @param id of thing
     * @param type of thing
     */
    public void initialize(long id, int classID, byte type, AssetManager assetManager);
    
    /**
     * If thing is not initialized 
     * @return 
     */
    public boolean isInitialized();
    
    /**
     * destroy spatial, recreateble data to compress thing
     */
    public void compress();
    
    /**
     * recreate compressed thing
     */
    public void recreate();
    
    public boolean isCompressed();
}
