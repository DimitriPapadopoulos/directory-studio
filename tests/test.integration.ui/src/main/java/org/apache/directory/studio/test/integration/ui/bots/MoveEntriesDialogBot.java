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
package org.apache.directory.studio.test.integration.ui.bots;


import org.apache.directory.studio.ldapbrowser.core.BrowserCoreMessages;


public class MoveEntriesDialogBot extends DialogBot
{

    public MoveEntriesDialogBot()
    {
        super( "Move Entries" );
        super.setWaitAfterClickOkButton( true, BrowserCoreMessages.jobs__move_entry_name_1 );
    }


    public void setParentText( String text )
    {
        bot.comboBox().setText( text );
    }


    public String getParentText()
    {
        return bot.comboBox().getText();
    }


    public SelectDnDialogBot clickBrowseButtonExpectingSelectDnDialog()
    {
        super.clickButton( "Browse..." );
        return new SelectDnDialogBot();
    }

}
