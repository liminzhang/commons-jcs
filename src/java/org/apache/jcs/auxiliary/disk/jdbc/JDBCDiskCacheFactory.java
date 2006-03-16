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

import org.apache.jcs.auxiliary.AuxiliaryCache;
import org.apache.jcs.auxiliary.AuxiliaryCacheAttributes;
import org.apache.jcs.auxiliary.AuxiliaryCacheFactory;
import org.apache.jcs.engine.behavior.ICompositeCacheManager;

/**
 * This factory should create mysql disk caches.
 * 
 * @author Aaron Smuts
 * 
 */
public class JDBCDiskCacheFactory
    implements AuxiliaryCacheFactory
{

    private String name = "MysqlDiskCacheFactory";

    /**
     * This factory method should create an instance of the mysqlcache.
     */
    public AuxiliaryCache createCache( AuxiliaryCacheAttributes rawAttr, ICompositeCacheManager arg1 )
    {
        JDBCDiskCacheManager mgr = JDBCDiskCacheManager.getInstance( (JDBCDiskCacheAttributes)rawAttr );
        return mgr.getCache( (JDBCDiskCacheAttributes)rawAttr );
    }

    /**
     * The name of the factory.
     */
    public void setName( String nameArg )
    {
        name = nameArg;
    }

    /**
     * Returns the display name
     */
    public String getName()
    {
        return name;
    }

}
