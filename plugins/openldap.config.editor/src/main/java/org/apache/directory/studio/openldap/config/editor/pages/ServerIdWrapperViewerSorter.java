/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.studio.openldap.config.editor.pages;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;

/**
 * This class defines a sorter for a ServerID wrapper viewer.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class ServerIdWrapperViewerSorter extends ViewerSorter
{
    public int compare( Viewer viewer, Object e1, Object e2 )
    {
        if ( ( e1 != null ) && ( e2 != null ) && ( e1 instanceof ServerIdWrapper )
            && ( e2 instanceof ServerIdWrapper ) )
        {
            ServerIdWrapper serverIdWrapper1 = ( ServerIdWrapper ) e1;
            ServerIdWrapper serverIdWrapper2 = ( ServerIdWrapper ) e2;
            int serverId1 = serverIdWrapper1.getServerId();
            int serverId2 = serverIdWrapper2.getServerId();

            if ( serverId1 > serverId2 )
            {
                return 1;
            }
            else if ( serverId1 < serverId2 )
            {
                return -1;
            }
            else 
            {
                // This is actually an error...
                return 1;
            }
        }

        return super.compare( viewer, e1, e2 );
    }
}
