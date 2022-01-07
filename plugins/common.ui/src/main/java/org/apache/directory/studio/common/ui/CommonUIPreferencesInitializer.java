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

package org.apache.directory.studio.common.ui;


import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;


/**
 * This class is used to set default preference values.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class CommonUIPreferencesInitializer extends AbstractPreferenceInitializer
{
    /**
     * {@inheritDoc}
     */
    public void initializeDefaultPreferences()
    {
        IPreferenceStore store = CommonUIPlugin.getDefault().getPreferenceStore();

        // Actual colors are defined in default.css and dark.css
        String dflt = IPreferenceStore.STRING_DEFAULT_DEFAULT;
        store.setDefault( CommonUIConstants.DEFAULT_COLOR, dflt );
        store.setDefault( CommonUIConstants.DISABLED_COLOR, dflt );
        store.setDefault( CommonUIConstants.ERROR_COLOR, dflt );
        store.setDefault( CommonUIConstants.COMMENT_COLOR, dflt );
        store.setDefault( CommonUIConstants.KEYWORD_1_COLOR, dflt );
        store.setDefault( CommonUIConstants.KEYWORD_2_COLOR, dflt );
        store.setDefault( CommonUIConstants.OBJECT_CLASS_COLOR, dflt );
        store.setDefault( CommonUIConstants.ATTRIBUTE_TYPE_COLOR, dflt );
        store.setDefault( CommonUIConstants.VALUE_COLOR, dflt );
        store.setDefault( CommonUIConstants.OID_COLOR, dflt );
        store.setDefault( CommonUIConstants.SEPARATOR_COLOR, dflt );
        store.setDefault( CommonUIConstants.ADD_COLOR, dflt );
        store.setDefault( CommonUIConstants.DELETE_COLOR, dflt );
        store.setDefault( CommonUIConstants.MODIFY_COLOR, dflt );
        store.setDefault( CommonUIConstants.RENAME_COLOR, dflt );
    }

}
