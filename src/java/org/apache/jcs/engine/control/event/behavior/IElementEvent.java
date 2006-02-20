package org.apache.jcs.engine.control.event.behavior;

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

import java.io.Serializable;

/**
 * Defines how an element event object should behave.
 *  
 */
public interface IElementEvent
    extends Serializable
{

    /**
     * Gets the elementEvent attribute of the IElementEvent object. This code is
     * Contained in the IElememtEventConstants class.
     * 
     * @return The elementEvent value
     */
    public int getElementEvent();

}
