package org.apache.jcs.auxiliary.disk.block;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.auxiliary.AuxiliaryCacheAttributes;
import org.apache.jcs.auxiliary.disk.AbstractDiskCache;
import org.apache.jcs.engine.CacheConstants;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.IElementSerializer;
import org.apache.jcs.engine.control.group.GroupAttrName;
import org.apache.jcs.engine.control.group.GroupId;
import org.apache.jcs.engine.stats.StatElement;
import org.apache.jcs.engine.stats.Stats;
import org.apache.jcs.engine.stats.behavior.IStatElement;
import org.apache.jcs.engine.stats.behavior.IStats;

/**
 * There is one BlockDiskCache per region. It manages the key and data store.
 * <p>
 * @author Aaron Smuts
 */
public class BlockDiskCache<K extends Serializable, V extends Serializable>
    extends AbstractDiskCache<K, V>
{
    /** Don't change */
    private static final long serialVersionUID = 1L;

    /** The logger. */
    protected static final Log log = LogFactory.getLog( BlockDiskCache.class );

    /** The name to prefix all log messages with. */
    private final String logCacheName;

    /** The name of the file to store data. */
    private final String fileName;

    /** The data access object */
    private BlockDisk dataFile;

    /** Attributes governing the behavior of the block disk cache. */
    private final BlockDiskCacheAttributes blockDiskCacheAttributes;

    /** The root directory for keys and data. */
    private final File rootDirectory;

    /** Store, loads, and persists the keys */
    private BlockDiskKeyStore<K> keyStore;

    /**
     * Use this lock to synchronize reads and writes to the underlying storage mechanism. We don't
     * need a reentrant lock, since we only lock one level.
     */
    private final ReentrantReadWriteLock storageLock = new ReentrantReadWriteLock();

    /**
     * Constructs the BlockDisk after setting up the root directory.
     * <p>
     * @param cacheAttributes
     */
    public BlockDiskCache( BlockDiskCacheAttributes cacheAttributes )
    {
        this( cacheAttributes, null );
    }

    /**
     * Constructs the BlockDisk after setting up the root directory.
     * <p>
     * @param cacheAttributes
     * @param elementSerializer used if supplied, the super's super will not set a null
     */
    public BlockDiskCache( BlockDiskCacheAttributes cacheAttributes, IElementSerializer elementSerializer )
    {
        super( cacheAttributes );
        setElementSerializer( elementSerializer );

        this.blockDiskCacheAttributes = cacheAttributes;
        this.logCacheName = "Region [" + getCacheName() + "] ";

        if ( log.isInfoEnabled() )
        {
            log.info( logCacheName + "Constructing BlockDiskCache with attributes " + cacheAttributes );
        }

        this.fileName = getCacheName();
        String rootDirName = cacheAttributes.getDiskPath();
        this.rootDirectory = new File( rootDirName );
        this.rootDirectory.mkdirs();

        if ( log.isInfoEnabled() )
        {
            log.info( logCacheName + "Cache file root directory: [" + rootDirName + "]" );
        }

        try
        {
            if ( this.blockDiskCacheAttributes.getBlockSizeBytes() > 0 )
            {
                this.dataFile = new BlockDisk( new File( rootDirectory, fileName + ".data" ),
                                               this.blockDiskCacheAttributes.getBlockSizeBytes() );
            }
            else
            {
                this.dataFile = new BlockDisk( new File( rootDirectory, fileName + ".data" ), getElementSerializer() );
            }

            keyStore = new BlockDiskKeyStore<K>( this.blockDiskCacheAttributes, this );

            boolean alright = verifyDisk();

            if ( keyStore.size() == 0 || !alright )
            {
                this.reset();
            }

            // Initialization finished successfully, so set alive to true.
            alive = true;
            if ( log.isInfoEnabled() )
            {
                log.info( logCacheName + "Block Disk Cache is alive." );
            }
        }
        catch ( IOException e )
        {
            log.error( logCacheName + "Failure initializing for fileName: " + fileName + " and root directory: "
                + rootDirName, e );
        }
    }

    /**
     * We need to verify that the file on disk uses the same block size and that the file is the
     * proper size.
     * <p>
     * @return true if it looks ok
     */
    protected boolean verifyDisk()
    {
        boolean alright = false;
        // simply try to read a few. If it works, then the file is probably ok.
        // TODO add more.

        storageLock.readLock().lock();

        try
        {
            int maxToTest = 100;
            int count = 0;
            Iterator<Map.Entry<K, int[]>> it = this.keyStore.entrySet().iterator();
            while ( it.hasNext() && count < maxToTest )
            {
                count++;
                Map.Entry<K, int[]> entry = it.next();
                Object data = this.dataFile.read( entry.getValue() );
                if ( data == null )
                {
                    throw new Exception( logCacheName + "Couldn't find data for key [" + entry.getKey() + "]" );
                }
            }
            alright = true;
        }
        catch ( Exception e )
        {
            log.warn( logCacheName + "Problem verifying disk.  Message [" + e.getMessage() + "]" );
            alright = false;
        }
        finally
        {
            storageLock.readLock().unlock();
        }

        return alright;
    }

    /**
     * This requires a full iteration through the keys.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.disk.AbstractDiskCache#getGroupKeys(java.lang.String)
     */
    @Override
    public Set<K> getGroupKeys( String groupName )
    {
        GroupId groupId = new GroupId( cacheName, groupName );
        HashSet<K> keys = new HashSet<K>();

        storageLock.readLock().lock();

        try
        {
            for ( K key : this.keyStore.keySet())
            {
                if ( key instanceof GroupAttrName && ( (GroupAttrName<?>) key ).groupId.equals( groupId ) )
                {
                    @SuppressWarnings("unchecked") // Type checked with instanceof
                    K newKey = ((GroupAttrName<K>) key ).attrName;
                    keys.add( newKey );
                }
            }
        }
        finally
        {
            storageLock.readLock().unlock();
        }

        return keys;
    }

    /**
     * Gets matching items from the cache.
     * <p>
     * @param pattern
     * @return a map of K key to ICacheElement<K, V> element, or an empty map if there is no
     *         data in cache matching keys
     */
    @Override
    public Map<K, ICacheElement<K, V>> processGetMatching( String pattern )
    {
        Map<K, ICacheElement<K, V>> elements = new HashMap<K, ICacheElement<K, V>>();

        Set<K> keyArray = null;
        storageLock.readLock().lock();
        try
        {
            keyArray = new HashSet<K>(keyStore.keySet());
        }
        finally
        {
            storageLock.readLock().unlock();
        }

        Set<K> matchingKeys = getKeyMatcher().getMatchingKeysFromArray( pattern, keyArray );

        for (K key : matchingKeys)
        {
            ICacheElement<K, V> element = processGet( key );
            if ( element != null )
            {
                elements.put( key, element );
            }
        }

        return elements;
    }

    /**
     * Returns the number of keys.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.disk.AbstractDiskCache#getSize()
     */
    @Override
    public int getSize()
    {
        return this.keyStore.size();
    }

    /**
     * Gets the ICacheElement<K, V> for the key if it is in the cache. The program flow is as follows:
     * <ol>
     * <li>Make sure the disk cache is alive.</li> <li>Get a read lock.</li> <li>See if the key is
     * in the key store.</li> <li>If we found a key, ask the BlockDisk for the object at the
     * blocks..</li> <li>Release the lock.</li>
     * </ol>
     * (non-Javadoc)
     * @param key
     * @return ICacheElement
     * @see org.apache.jcs.auxiliary.disk.AbstractDiskCache#doGet(java.io.Serializable)
     */
    @Override
    protected ICacheElement<K, V> processGet( K key )
    {
        if ( !alive )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( logCacheName + "No longer alive so returning null for key = " + key );
            }
            return null;
        }

        if ( log.isDebugEnabled() )
        {
            log.debug( logCacheName + "Trying to get from disk: " + key );
        }

        ICacheElement<K, V> object = null;
        storageLock.readLock().lock();

        try
        {
            int[] ded = this.keyStore.get( key );
            if ( ded != null )
            {
                object = this.dataFile.read( ded );
            }
        }
        catch ( IOException ioe )
        {
            log.error( logCacheName + "Failure getting from disk--IOException, key = " + key, ioe );
            reset();
        }
        catch ( Exception e )
        {
            log.error( logCacheName + "Failure getting from disk, key = " + key, e );
        }
        finally
        {
            storageLock.readLock().unlock();
        }

        return object;
    }

    /**
     * Writes an element to disk. The program flow is as follows:
     * <ol>
     * <li>Acquire write lock.</li> <li>See id an item exists for this key.</li> <li>If an item
     * already exists, add its blocks to the remove list.</li> <li>Have the Block disk write the
     * item.</li> <li>Create a descriptor and add it to the key map.</li> <li>Release the write
     * lock.</li>
     * </ol>
     * @param element
     * @see org.apache.jcs.auxiliary.disk.AbstractDiskCache#doUpdate(org.apache.jcs.engine.behavior.ICacheElement)
     */
    @Override
    protected void processUpdate( ICacheElement<K, V> element )
    {
        if ( !alive )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( logCacheName + "No longer alive; aborting put of key = " + element.getKey() );
            }
            return;
        }

        int[] old = null;

        // make sure this only locks for one particular cache region
        storageLock.writeLock().lock();

        try
        {
            old = this.keyStore.get( element.getKey() );

            if ( old != null )
            {
                this.dataFile.freeBlocks( old );
            }

            int[] blocks = this.dataFile.write( element );

            this.keyStore.put( element.getKey(), blocks );

            if ( log.isDebugEnabled() )
            {
                log.debug( logCacheName + "Put to file [" + fileName + "] key [" + element.getKey() + "]" );
            }
        }
        catch ( IOException e )
        {
            log.error( logCacheName + "Failure updating element, key: " + element.getKey() + " old: " + Arrays.toString(old), e );
        }
        finally
        {
            storageLock.writeLock().unlock();
        }

        if ( log.isDebugEnabled() )
        {
            log.debug( logCacheName + "Storing element on disk, key: " + element.getKey() );
        }
    }

    /**
     * Returns true if the removal was successful; or false if there is nothing to remove. Current
     * implementation always result in a disk orphan.
     * <p>
     * (non-Javadoc)
     * @param key
     * @return true if removed anything
     * @see org.apache.jcs.auxiliary.disk.AbstractDiskCache#doRemove(java.io.Serializable)
     */
    @Override
    protected boolean processRemove( K key )
    {
        if ( !alive )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( logCacheName + "No longer alive so returning false for key = " + key );
            }
            return false;
        }

        boolean reset = false;
        boolean removed = false;

        storageLock.writeLock().lock();

        try
        {
            if ( key instanceof String && key.toString().endsWith( CacheConstants.NAME_COMPONENT_DELIMITER ) )
            {
                // remove all keys of the same name group.
                Iterator<Map.Entry<K, int[]>> iter = this.keyStore.entrySet().iterator();

                while ( iter.hasNext() )
                {
                    Map.Entry<K, int[]> entry = iter.next();
                    K k = entry.getKey();

                    if ( k instanceof String && k.toString().startsWith( key.toString() ) )
                    {
                        int[] ded = this.keyStore.get( key );
                        this.dataFile.freeBlocks( ded );
                        iter.remove();
                        removed = true;
                        // TODO this needs to update the remove count separately
                    }
                }
            }
            else if ( key instanceof GroupAttrName && ((GroupAttrName<?>)key).attrName == null )
            {
                // remove all keys of the same name hierarchy.
                Iterator<Map.Entry<K, int[]>> iter = this.keyStore.entrySet().iterator();
                while ( iter.hasNext() )
                {
                    Map.Entry<K, int[]> entry = iter.next();
                    K k = entry.getKey();

                    if ( k instanceof GroupAttrName &&
                        ((GroupAttrName<?>)k).groupId.equals(((GroupAttrName<?>)key).groupId))
                    {
                        int[] ded = this.keyStore.get( key );
                        this.dataFile.freeBlocks( ded );
                        iter.remove();
                        removed = true;
                    }
                }
            }
            else
            {
                // remove single item.
                int[] ded = this.keyStore.remove( key );
                removed = ( ded != null );
                if ( ded != null )
                {
                    this.dataFile.freeBlocks( ded );
                }

                if ( log.isDebugEnabled() )
                {
                    log.debug( logCacheName + "Disk removal: Removed from key hash, key [" + key + "] removed = "
                        + removed );
                }
            }
        }
        catch ( Exception e )
        {
            log.error( logCacheName + "Problem removing element.", e );
            reset = true;
        }
        finally
        {
            storageLock.writeLock().unlock();
        }

        if ( reset )
        {
            reset();
        }

        return removed;
    }

    /**
     * Resets the keyfile, the disk file, and the memory key map.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.disk.AbstractDiskCache#doRemoveAll()
     */
    @Override
    protected void processRemoveAll()
    {
        reset();
    }

    /**
     * Dispose of the disk cache in a background thread. Joins against this thread to put a cap on
     * the disposal time.
     * <p>
     * TODO make dispose window configurable.
     */
    @Override
    public void processDispose()
    {
        Runnable disR = new Runnable()
        {
            public void run()
            {
                try
                {
                    disposeInternal();
                }
                catch ( InterruptedException e )
                {
                    log.warn( "Interrupted while diposing." );
                }
            }
        };
        Thread t = new Thread( disR, "BlockDiskCache-DisposalThread" );
        t.start();
        // wait up to 60 seconds for dispose and then quit if not done.
        try
        {
            t.join( 60 * 1000 );
        }
        catch ( InterruptedException ex )
        {
            log.error( logCacheName + "Interrupted while waiting for disposal thread to finish.", ex );
        }
    }

    /**
     * Internal method that handles the disposal.
     * @throws InterruptedException
     */
    protected void disposeInternal()
        throws InterruptedException
    {
        if ( !alive )
        {
            log.error( logCacheName + "Not alive and dispose was called, filename: " + fileName );
            return;
        }
        storageLock.writeLock().lock();
        try
        {
            // Prevents any interaction with the cache while we're shutting down.
            alive = false;

            this.keyStore.saveKeys();

            try
            {
                if ( log.isDebugEnabled() )
                {
                    log.debug( logCacheName + "Closing files, base filename: " + fileName );
                }
                dataFile.close();
                // dataFile = null;

                // TOD make a close
                // keyFile.close();
                // keyFile = null;
            }
            catch ( IOException e )
            {
                log.error( logCacheName + "Failure closing files in dispose, filename: " + fileName, e );
            }
        }
        finally
        {
            storageLock.writeLock().unlock();
        }

        if ( log.isInfoEnabled() )
        {
            log.info( logCacheName + "Shutdown complete." );
        }
    }

    /**
     * Returns the attributes.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getAuxiliaryCacheAttributes()
     */
    public AuxiliaryCacheAttributes getAuxiliaryCacheAttributes()
    {
        return this.blockDiskCacheAttributes;
    }

    /**
     * Reset effectively clears the disk cache, creating new files, recyclebins, and keymaps.
     * <p>
     * It can be used to handle errors by last resort, force content update, or removeall.
     */
    private void reset()
    {
        if ( log.isWarnEnabled() )
        {
            log.warn( logCacheName + "Resetting cache" );
        }

        try
        {
            storageLock.writeLock().lock();

            this.keyStore.reset();

            if ( dataFile != null )
            {
                dataFile.reset();
            }
        }
        catch ( IOException e )
        {
            log.error( logCacheName + "Failure resetting state", e );
        }
        finally
        {
            storageLock.writeLock().unlock();
        }
    }

    /**
     * Add these blocks to the emptyBlock list.
     * <p>
     * @param blocksToFree
     */
    protected void freeBlocks( int[] blocksToFree )
    {
        this.dataFile.freeBlocks( blocksToFree );
    }

    /**
     * Gets basic stats for the disk cache.
     * <p>
     * @return String
     */
    @Override
    public String getStats()
    {
        return getStatistics().toString();
    }

    /**
     * Returns info about the disk cache.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getStatistics()
     */
    @Override
    public IStats getStatistics()
    {
        IStats stats = new Stats();
        stats.setTypeName( "Block Disk Cache" );

        ArrayList<IStatElement> elems = new ArrayList<IStatElement>();

        IStatElement se = null;

        se = new StatElement();
        se.setName( "Is Alive" );
        se.setData( "" + alive );
        elems.add( se );

        se = new StatElement();
        se.setName( "Key Map Size" );
        se.setData( "" + this.keyStore.size() );
        elems.add( se );

        try
        {
            se = new StatElement();
            se.setName( "Data File Length" );
            if ( this.dataFile != null )
            {
                se.setData( "" + this.dataFile.length() );
            }
            else
            {
                se.setData( "-1" );
            }
            elems.add( se );
        }
        catch ( Exception e )
        {
            log.error( e );
        }

        se = new StatElement();
        se.setName( "Block Size Bytes" );
        se.setData( "" + this.dataFile.getBlockSizeBytes() );
        elems.add( se );

        se = new StatElement();
        se.setName( "Number Of Blocks" );
        se.setData( "" + this.dataFile.getNumberOfBlocks() );
        elems.add( se );

        se = new StatElement();
        se.setName( "Average Put Size Bytes" );
        se.setData( "" + this.dataFile.getAveragePutSizeBytes() );
        elems.add( se );

        se = new StatElement();
        se.setName( "Empty Blocks" );
        se.setData( "" + this.dataFile.getEmptyBlocks() );
        elems.add( se );

        // get the stats from the super too
        // get as array, convert to list, add list to our outer list
        IStats sStats = super.getStatistics();
        IStatElement[] sSEs = sStats.getStatElements();
        List<IStatElement> sL = Arrays.asList( sSEs );
        elems.addAll( sL );

        // get an array and put them in the Stats object
        IStatElement[] ses = elems.toArray( new StatElement[0] );
        stats.setStatElements( ses );

        return stats;
    }

    /**
     * This is used by the event logging.
     * <p>
     * @return the location of the disk, either path or ip.
     */
    @Override
    protected String getDiskLocation()
    {
        return dataFile.getFilePath();
    }
}
