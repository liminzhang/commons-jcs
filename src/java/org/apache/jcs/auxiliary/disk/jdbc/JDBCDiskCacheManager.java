package org.apache.jcs.auxiliary.disk.jdbc;

/*
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Enumeration;
import java.util.Hashtable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.auxiliary.AuxiliaryCache;
import org.apache.jcs.auxiliary.AuxiliaryCacheManager;

/**
 * This manages instances of the jdbc disk cache. It maintains one for each
 * region. One for all regions would work, but this gives us more detailed stats
 * by region.
 * 
 */
public class JDBCDiskCacheManager
    implements AuxiliaryCacheManager
{

    private static final long serialVersionUID = -8258856770927857896L;

    private static final Log log = LogFactory.getLog( JDBCDiskCacheManager.class );

    private static int clients;

    private static Hashtable caches = new Hashtable();

    private static JDBCDiskCacheManager instance;

    private static JDBCDiskCacheAttributes defaultCattr;

    /**
     * Constructor for the HSQLCacheManager object
     * 
     * @param cattr
     */
    private JDBCDiskCacheManager( JDBCDiskCacheAttributes cattr )
    {
        defaultCattr = cattr;
    }

    /**
     * Gets the defaultCattr attribute of the HSQLCacheManager object
     * 
     * @return The defaultCattr value
     */
    public JDBCDiskCacheAttributes getDefaultCattr()
    {
        return defaultCattr;
    }

    /**
     * Gets the instance attribute of the HSQLCacheManager class
     * 
     * @param cattr
     * 
     * @return The instance value
     */
    public static JDBCDiskCacheManager getInstance( JDBCDiskCacheAttributes cattr )
    {
        synchronized ( JDBCDiskCacheManager.class )
        {
            if ( instance == null )
            {
                instance = new JDBCDiskCacheManager( cattr );
            }
        }

        clients++;
        return instance;
    }

    /**
     * Gets the cache attribute of the HSQLCacheManager object
     * 
     * @param cacheName
     * 
     * @return The cache value
     */
    public AuxiliaryCache getCache( String cacheName )
    {
        JDBCDiskCacheAttributes cattr = (JDBCDiskCacheAttributes) defaultCattr.copy();
        cattr.setCacheName( cacheName );
        return getCache( cattr );
    }

    /**
     * Gets the cache attribute of the HSQLCacheManager object
     * 
     * @param cattr
     * 
     * @return The cache value
     */
    public AuxiliaryCache getCache( JDBCDiskCacheAttributes cattr )
    {
        AuxiliaryCache raf = null;

        log.debug( "cacheName = " + cattr.getCacheName() );

        synchronized ( caches )
        {
            raf = (AuxiliaryCache) caches.get( cattr.getCacheName() );

            if ( raf == null )
            {
                raf = new JDBCDiskCache( cattr );
                caches.put( cattr.getCacheName(), raf );
            }
        }

        if ( log.isDebugEnabled() )
        {
            log.debug( "JDBC cache = " + raf );
        }

        return raf;
    }

    /**
     * 
     * @param name
     */
    public void freeCache( String name )
    {
        JDBCDiskCache raf = (JDBCDiskCache) caches.get( name );
        if ( raf != null )
        {

            raf.dispose();

        }
    }

    /**
     * Gets the cacheType attribute of the HSQLCacheManager object
     * 
     * @return The cacheType value
     */
    public int getCacheType()
    {
        return DISK_CACHE;
    }

    /** Disposes of all regions. */
    public void release()
    {
        // Wait until called by the last client
        if ( --clients != 0 )
        {
            return;
        }
        synchronized ( caches )
        {
            Enumeration allCaches = caches.elements();

            while ( allCaches.hasMoreElements() )
            {
                JDBCDiskCache raf = (JDBCDiskCache) allCaches.nextElement();
                if ( raf != null )
                {
                    raf.dispose();
                }
            }
        }
    }
}
