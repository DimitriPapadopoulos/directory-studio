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
package org.apache.directory.studio.connection.core.io.api;


import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.naming.directory.SearchControls;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;

import org.apache.directory.api.ldap.codec.api.DefaultConfigurableBinaryAttributeDetector;
import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.message.AddRequest;
import org.apache.directory.api.ldap.model.message.AddRequestImpl;
import org.apache.directory.api.ldap.model.message.AddResponse;
import org.apache.directory.api.ldap.model.message.AliasDerefMode;
import org.apache.directory.api.ldap.model.message.BindRequest;
import org.apache.directory.api.ldap.model.message.BindRequestImpl;
import org.apache.directory.api.ldap.model.message.BindResponse;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.message.DeleteRequest;
import org.apache.directory.api.ldap.model.message.DeleteRequestImpl;
import org.apache.directory.api.ldap.model.message.DeleteResponse;
import org.apache.directory.api.ldap.model.message.ExtendedRequest;
import org.apache.directory.api.ldap.model.message.ExtendedResponse;
import org.apache.directory.api.ldap.model.message.LdapResult;
import org.apache.directory.api.ldap.model.message.ModifyDnRequest;
import org.apache.directory.api.ldap.model.message.ModifyDnRequestImpl;
import org.apache.directory.api.ldap.model.message.ModifyDnResponse;
import org.apache.directory.api.ldap.model.message.ModifyRequest;
import org.apache.directory.api.ldap.model.message.ModifyRequestImpl;
import org.apache.directory.api.ldap.model.message.ModifyResponse;
import org.apache.directory.api.ldap.model.message.Referral;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.message.ResultResponse;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchRequestImpl;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.url.LdapUrl;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.ldap.client.api.SaslCramMd5Request;
import org.apache.directory.ldap.client.api.SaslDigestMd5Request;
import org.apache.directory.ldap.client.api.SaslGssApiRequest;
import org.apache.directory.ldap.client.api.exception.InvalidConnectionException;
import org.apache.directory.studio.common.core.jobs.StudioProgressMonitor;
import org.apache.directory.studio.connection.core.Connection;
import org.apache.directory.studio.connection.core.Connection.AliasDereferencingMethod;
import org.apache.directory.studio.connection.core.Connection.ReferralHandlingMethod;
import org.apache.directory.studio.connection.core.ConnectionCoreConstants;
import org.apache.directory.studio.connection.core.ConnectionCorePlugin;
import org.apache.directory.studio.connection.core.ConnectionParameter;
import org.apache.directory.studio.connection.core.ConnectionParameter.EncryptionMethod;
import org.apache.directory.studio.connection.core.IAuthHandler;
import org.apache.directory.studio.connection.core.ICredentials;
import org.apache.directory.studio.connection.core.ILdapLogger;
import org.apache.directory.studio.connection.core.Messages;
import org.apache.directory.studio.connection.core.ReferralsInfo;
import org.apache.directory.studio.connection.core.io.ConnectionWrapper;
import org.apache.directory.studio.connection.core.io.ConnectionWrapperUtils;
import org.apache.directory.studio.connection.core.io.StudioLdapException;
import org.apache.directory.studio.connection.core.io.StudioTrustManager;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.osgi.util.NLS;


/**
 * A ConnectionWrapper is a wrapper for a real directory connection implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DirectoryApiConnectionWrapper implements ConnectionWrapper
{
    /** The search request number */
    private static int searchRequestNum = 0;

    /** The Studio connection  */
    private Connection connection;

    /** The LDAP connection */
    private LdapNetworkConnection ldapConnection;

    /** The binary attribute detector */
    private DefaultConfigurableBinaryAttributeDetector binaryAttributeDetector;

    /** The current job thread */
    private Thread jobThread;

    /**
     * Creates a new instance of DirectoryApiConnectionWrapper.
     * 
     * @param connection the connection
     */
    public DirectoryApiConnectionWrapper( Connection connection )
    {
        this.connection = connection;
    }


    /**
     * {@inheritDoc}
     */
    public void connect( StudioProgressMonitor monitor )
    {
        ldapConnection = null;
        jobThread = null;

        try
        {
            doConnect( monitor );
        }
        catch ( Exception e )
        {
            disconnect();
            monitor.reportError( e );
        }
    }


    private void doConnect( final StudioProgressMonitor monitor ) throws Exception
    {
        ldapConnection = null;

        LdapConnectionConfig ldapConnectionConfig = new LdapConnectionConfig();
        ldapConnectionConfig.setLdapHost( connection.getHost() );
        ldapConnectionConfig.setLdapPort( connection.getPort() );

        long timeoutMillis = connection.getTimeoutMillis();

        if ( timeoutMillis < 0 )
        {
            timeoutMillis = 30000L;
        }

        ldapConnectionConfig.setTimeout( timeoutMillis );

        binaryAttributeDetector = new DefaultConfigurableBinaryAttributeDetector();
        ldapConnectionConfig.setBinaryAttributeDetector( binaryAttributeDetector );

        AtomicReference<StudioTrustManager> studioTrustmanager = new AtomicReference<>();

        if ( ( connection.getEncryptionMethod() == EncryptionMethod.LDAPS )
            || ( connection.getEncryptionMethod() == EncryptionMethod.START_TLS ) )
        {
            ldapConnectionConfig.setUseSsl( connection.getEncryptionMethod() == EncryptionMethod.LDAPS );
            ldapConnectionConfig.setUseTls( connection.getEncryptionMethod() == EncryptionMethod.START_TLS );

            try
            {
                // get default trust managers (using JVM "cacerts" key store)
                TrustManagerFactory factory = TrustManagerFactory.getInstance( TrustManagerFactory
                    .getDefaultAlgorithm() );
                factory.init( ( KeyStore ) null );
                TrustManager[] defaultTrustManagers = factory.getTrustManagers();

                // create wrappers around the trust managers
                StudioTrustManager[] trustManagers = new StudioTrustManager[defaultTrustManagers.length];

                for ( int i = 0; i < defaultTrustManagers.length; i++ )
                {
                    trustManagers[i] = new StudioTrustManager( ( X509TrustManager ) defaultTrustManagers[i] );
                    trustManagers[i].setHost( connection.getHost() );
                }
                studioTrustmanager.set( trustManagers[0] );

                ldapConnectionConfig.setTrustManagers( trustManagers );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
                throw new RuntimeException( e );
            }
        }

        InnerRunnable runnable = new InnerRunnable()
        {
            public void run()
            {
                /*
                 * Use local temp variable while the connection is being established and secured.
                 * This process can take a while and the user might be asked to inspect the server
                 * certificate. During that process the connection must not be used.
                 */
                LdapNetworkConnection ldapConnectionUnderConstruction = null;
                try
                {
                    // Set lower timeout for connecting
                    long oldTimeout = ldapConnectionConfig.getTimeout();
                    ldapConnectionConfig.setTimeout( Math.min( oldTimeout, 5000L ) );

                    // Connecting
                    ldapConnectionUnderConstruction = new LdapNetworkConnection( ldapConnectionConfig );
                    ldapConnectionUnderConstruction.connect();

                    // DIRSTUDIO-1219: Establish TLS layer if TLS is enabled and SSL is not
                    if ( ldapConnectionConfig.isUseTls() && !ldapConnectionConfig.isUseSsl() )
                    {
                        ldapConnectionUnderConstruction.startTls();
                    }

                    // Set original timeout again
                    ldapConnectionConfig.setTimeout( oldTimeout );
                    ldapConnectionUnderConstruction.setTimeOut( oldTimeout );

                    // Now set the LDAP connection once the (optional) security layer is in place
                    ldapConnection = ldapConnectionUnderConstruction;

                    if ( !isConnected() )
                    {
                        throw new Exception( Messages.DirectoryApiConnectionWrapper_UnableToConnect );
                    }

                    // DIRSTUDIO-1219: Verify secure connection if ldaps:// or StartTLS is configured
                    if ( ldapConnectionConfig.isUseTls() || ldapConnectionConfig.isUseSsl() )
                    {
                        if ( !isSecured() )
                        {
                            throw new Exception( Messages.DirectoryApiConnectionWrapper_UnsecuredConnection );
                        }
                    }
                }
                catch ( Exception e )
                {
                    exception = toStudioLdapException( e );

                    try
                    {
                        if ( ldapConnectionUnderConstruction != null )
                        {
                            ldapConnectionUnderConstruction.close();
                        }
                    }
                    catch ( Exception exception )
                    {
                        // Nothing to do
                    }
                    finally
                    {
                        ldapConnection = null;
                        binaryAttributeDetector = null;
                    }
                }
            }
        };

        runAndMonitor( runnable, monitor );

        if ( runnable.getException() != null )
        {
            throw runnable.getException();
        }
    }


    /**
     * {@inheritDoc}
     */
    public void disconnect()
    {
        if ( jobThread != null )
        {
            Thread t = jobThread;
            jobThread = null;
            t.interrupt();
        }
        if ( ldapConnection != null )
        {
            try
            {
                ldapConnection.close();
            }
            catch ( Exception e )
            {
                // ignore
            }
            ldapConnection = null;
            binaryAttributeDetector = null;
        }
    }


    /**
     * {@inheritDoc}
     */
    public void bind( StudioProgressMonitor monitor )
    {
        try
        {
            doBind( monitor );
        }
        catch ( Exception e )
        {
            disconnect();
            monitor.reportError( e );
        }
    }


    private BindResponse bindSimple( String bindPrincipal, String bindPassword ) throws LdapException
    {
        BindRequest bindRequest = new BindRequestImpl();
        bindRequest.setName( bindPrincipal );
        bindRequest.setCredentials( bindPassword );

        return ldapConnection.bind( bindRequest );
    }


    private void doBind( final StudioProgressMonitor monitor ) throws Exception
    {
        if ( isConnected() )
        {
            InnerRunnable runnable = new InnerRunnable()
            {
                public void run()
                {
                    try
                    {
                        BindResponse bindResponse = null;

                        // No Authentication
                        if ( connection.getConnectionParameter()
                            .getAuthMethod() == ConnectionParameter.AuthenticationMethod.NONE )
                        {
                            BindRequest bindRequest = new BindRequestImpl();
                            bindResponse = ldapConnection.bind( bindRequest );
                        }
                        else
                        {
                            // Setup credentials
                            IAuthHandler authHandler = ConnectionCorePlugin.getDefault().getAuthHandler();
                            if ( authHandler == null )
                            {
                                Exception exception = new Exception( Messages.model__no_auth_handler );
                                monitor.setCanceled( true );
                                monitor.reportError( Messages.model__no_auth_handler, exception );
                                throw exception;
                            }
                            ICredentials credentials = authHandler
                                .getCredentials( connection.getConnectionParameter() );
                            if ( credentials == null )
                            {
                                Exception exception = new Exception();
                                monitor.setCanceled( true );
                                monitor.reportError( Messages.model__no_credentials, exception );
                                throw exception;
                            }
                            if ( credentials.getBindPrincipal() == null || credentials.getBindPassword() == null )
                            {
                                Exception exception = new Exception( Messages.model__no_credentials );
                                monitor.reportError( Messages.model__no_credentials, exception );
                                throw exception;
                            }
                            String bindPrincipal = credentials.getBindPrincipal();
                            String bindPassword = credentials.getBindPassword();

                            switch ( connection.getConnectionParameter().getAuthMethod() )
                            {
                                case SIMPLE:
                                    // Simple Authentication
                                    bindResponse = bindSimple( bindPrincipal, bindPassword );
                                    break;

                                case SASL_CRAM_MD5:
                                    // CRAM-MD5 Authentication
                                    SaslCramMd5Request cramMd5Request = new SaslCramMd5Request();
                                    cramMd5Request.setUsername( bindPrincipal );
                                    cramMd5Request.setCredentials( bindPassword );
                                    cramMd5Request
                                        .setQualityOfProtection( connection.getConnectionParameter().getSaslQop() );
                                    cramMd5Request.setSecurityStrength( connection.getConnectionParameter()
                                        .getSaslSecurityStrength() );
                                    cramMd5Request.setMutualAuthentication( connection.getConnectionParameter()
                                        .isSaslMutualAuthentication() );

                                    bindResponse = ldapConnection.bind( cramMd5Request );
                                    break;

                                case SASL_DIGEST_MD5:
                                    // DIGEST-MD5 Authentication
                                    SaslDigestMd5Request digestMd5Request = new SaslDigestMd5Request();
                                    digestMd5Request.setUsername( bindPrincipal );
                                    digestMd5Request.setCredentials( bindPassword );
                                    digestMd5Request.setRealmName( connection.getConnectionParameter().getSaslRealm() );
                                    digestMd5Request.setQualityOfProtection( connection.getConnectionParameter()
                                        .getSaslQop() );
                                    digestMd5Request.setSecurityStrength( connection.getConnectionParameter()
                                        .getSaslSecurityStrength() );
                                    digestMd5Request.setMutualAuthentication( connection.getConnectionParameter()
                                        .isSaslMutualAuthentication() );

                                    bindResponse = ldapConnection.bind( digestMd5Request );
                                    break;

                                case SASL_GSSAPI:
                                    // GSSAPI Authentication
                                    SaslGssApiRequest gssApiRequest = new SaslGssApiRequest();

                                    Preferences preferences = ConnectionCorePlugin.getDefault().getPluginPreferences();
                                    boolean useKrb5SystemProperties = preferences
                                        .getBoolean( ConnectionCoreConstants.PREFERENCE_USE_KRB5_SYSTEM_PROPERTIES );
                                    String krb5LoginModule = preferences
                                        .getString( ConnectionCoreConstants.PREFERENCE_KRB5_LOGIN_MODULE );

                                    if ( !useKrb5SystemProperties )
                                    {
                                        gssApiRequest.setUsername( bindPrincipal );
                                        gssApiRequest.setCredentials( bindPassword );
                                        gssApiRequest.setQualityOfProtection( connection
                                            .getConnectionParameter().getSaslQop() );
                                        gssApiRequest.setSecurityStrength( connection
                                            .getConnectionParameter()
                                            .getSaslSecurityStrength() );
                                        gssApiRequest.setMutualAuthentication( connection
                                            .getConnectionParameter()
                                            .isSaslMutualAuthentication() );
                                        gssApiRequest
                                            .setLoginModuleConfiguration( new InnerConfiguration(
                                                krb5LoginModule ) );

                                        switch ( connection.getConnectionParameter().getKrb5Configuration() )
                                        {
                                            case FILE:
                                                gssApiRequest.setKrb5ConfFilePath( connection.getConnectionParameter()
                                                    .getKrb5ConfigurationFile() );
                                                break;
                                            case MANUAL:
                                                gssApiRequest.setRealmName( connection.getConnectionParameter()
                                                    .getKrb5Realm() );
                                                gssApiRequest.setKdcHost( connection.getConnectionParameter()
                                                    .getKrb5KdcHost() );
                                                gssApiRequest.setKdcPort( connection.getConnectionParameter()
                                                    .getKrb5KdcPort() );
                                                break;
                                            default:
                                                break;
                                        }
                                    }

                                    bindResponse = ldapConnection.bind( gssApiRequest );
                                    break;
                            }
                        }

                        checkResponse( bindResponse );
                    }
                    catch ( Exception e )
                    {
                        exception = toStudioLdapException( e );
                    }
                }
            };

            runAndMonitor( runnable, monitor );

            if ( runnable.getException() != null )
            {
                throw runnable.getException();
            }
        }
        else
        {
            throw new Exception( Messages.DirectoryApiConnectionWrapper_NoConnection );
        }
    }


    /***
     * {@inheritDoc}
     */
    public void unbind()
    {
        disconnect();
    }


    /**
     * {@inheritDoc}
     */
    public boolean isConnected()
    {
        return ( ldapConnection != null && ldapConnection.isConnected() );
    }


    /**
     * {@inheritDoc}
     */
    public boolean isSecured()
    {
        return isConnected() && ldapConnection.isSecured();
    }


    @Override
    public SSLSession getSslSession()
    {
        return isConnected() ? ldapConnection.getSslSession() : null;
    }


    /**
     * {@inheritDoc}
     */
    public void setBinaryAttributes( Collection<String> binaryAttributes )
    {
        if ( binaryAttributeDetector != null )
        {
            // Clear the initial list
            binaryAttributeDetector.setBinaryAttributes();

            // Add each binary attribute
            for ( String binaryAttribute : binaryAttributes )
            {
                binaryAttributeDetector.addBinaryAttribute( binaryAttribute );
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    public StudioSearchResultEnumeration search( final String searchBase, final String filter,
        final SearchControls searchControls, final AliasDereferencingMethod aliasesDereferencingMethod,
        final ReferralHandlingMethod referralsHandlingMethod, final Control[] controls,
        final StudioProgressMonitor monitor, final ReferralsInfo referralsInfo )
    {
        final long requestNum = searchRequestNum++;

        InnerRunnable runnable = new InnerRunnable()
        {
            public void run()
            {
                try
                {
                    // Preparing the search request
                    SearchRequest request = new SearchRequestImpl();
                    request.setBase( new Dn( searchBase ) );
                    ExprNode node = FilterParser.parse( filter, true );
                    request.setFilter( node );
                    request.setScope( convertSearchScope( searchControls ) );
                    if ( searchControls.getReturningAttributes() != null )
                    {
                        request.addAttributes( searchControls.getReturningAttributes() );
                    }
                    if ( controls != null )
                    {
                        request.addAllControls( controls );
                    }
                    request.setSizeLimit( searchControls.getCountLimit() );
                    request.setTimeLimit( searchControls.getTimeLimit() );
                    request.setDerefAliases( convertAliasDerefMode( aliasesDereferencingMethod ) );

                    // Performing the search operation
                    SearchCursor cursor = ldapConnection.search( request );

                    // Returning the result of the search
                    searchResultEnumeration = new StudioSearchResultEnumeration( connection, cursor, searchBase, filter,
                        searchControls, aliasesDereferencingMethod, referralsHandlingMethod, controls, requestNum,
                        monitor, referralsInfo );
                }
                catch ( Exception e )
                {
                    exception = toStudioLdapException( e );
                }

                for ( ILdapLogger logger : getLdapLoggers() )
                {
                    if ( searchResultEnumeration != null )
                    {
                        logger.logSearchRequest( connection, searchBase, filter, searchControls,
                            aliasesDereferencingMethod, controls, requestNum, exception );
                    }
                    else
                    {
                        logger.logSearchRequest( connection, searchBase, filter, searchControls,
                            aliasesDereferencingMethod, controls, requestNum, exception );
                        logger.logSearchResultDone( connection, 0, requestNum, exception );
                    }
                }
            }
        };

        try
        {
            checkConnectionAndRunAndMonitor( runnable, monitor );
        }
        catch ( Exception e )
        {
            monitor.reportError( e );
            return null;
        }

        if ( runnable.isCanceled() )
        {
            monitor.setCanceled( true );
        }
        if ( runnable.getException() != null )
        {
            monitor.reportError( runnable.getException() );
            return null;
        }
        else
        {
            return runnable.getResult();
        }
    }


    /**
     * Converts the search scope.
     *
     * @param searchControls
     *      the search controls
     * @return
     *      the associated search scope
     */
    private SearchScope convertSearchScope( SearchControls searchControls )
    {
        int scope = searchControls.getSearchScope();
        if ( scope == SearchControls.OBJECT_SCOPE )
        {
            return SearchScope.OBJECT;
        }
        else if ( scope == SearchControls.ONELEVEL_SCOPE )
        {
            return SearchScope.ONELEVEL;
        }
        else if ( scope == SearchControls.SUBTREE_SCOPE )
        {
            return SearchScope.SUBTREE;
        }
        else
        {
            return SearchScope.SUBTREE;
        }
    }


    /**
     * Converts the Alias Dereferencing method.
     *
     * @param aliasesDereferencingMethod
     *      the Alias Dereferencing method.
     * @return
     *      the converted Alias Dereferencing method.
     */
    private AliasDerefMode convertAliasDerefMode( AliasDereferencingMethod aliasesDereferencingMethod )
    {
        switch ( aliasesDereferencingMethod )
        {
            case ALWAYS:
                return AliasDerefMode.DEREF_ALWAYS;
            case FINDING:
                return AliasDerefMode.DEREF_FINDING_BASE_OBJ;
            case NEVER:
                return AliasDerefMode.NEVER_DEREF_ALIASES;
            case SEARCH:
                return AliasDerefMode.DEREF_IN_SEARCHING;
            default:
                return AliasDerefMode.DEREF_ALWAYS;
        }
    }


    /**
     * {@inheritDoc}
     */
    public void modifyEntry( final Dn dn, final Collection<Modification> modifications, final Control[] controls,
        final StudioProgressMonitor monitor, final ReferralsInfo referralsInfo )
    {
        if ( connection.isReadOnly() )
        {
            monitor
                .reportError(
                    new Exception( NLS.bind( Messages.error__connection_is_readonly, connection.getName() ) ) );
            return;
        }

        InnerRunnable runnable = new InnerRunnable()
        {
            public void run()
            {
                try
                {
                    // Preparing the modify request
                    ModifyRequest request = new ModifyRequestImpl();
                    request.setName( dn );
                    if ( modifications != null )
                    {
                        for ( Modification modification : modifications )
                        {
                            request.addModification( modification );
                        }
                    }
                    if ( controls != null )
                    {
                        request.addAllControls( controls );
                    }

                    // Performing the modify operation
                    ModifyResponse modifyResponse = ldapConnection.modify( request );

                    // Handle referral
                    ReferralHandlingDataConsumer consumer = referralHandlingData -> referralHandlingData.connectionWrapper
                        .modifyEntry( new Dn( referralHandlingData.referralDn ), modifications, controls, monitor,
                            referralHandlingData.newReferralsInfo );

                    if ( checkAndHandleReferral( modifyResponse, monitor, referralsInfo, consumer ) )
                    {
                        return;
                    }

                    // Checking the response
                    checkResponse( modifyResponse );
                }
                catch ( Exception e )
                {
                    exception = toStudioLdapException( e );
                }

                for ( ILdapLogger logger : getLdapLoggers() )
                {
                    logger.logChangetypeModify( connection, dn, modifications, controls, exception );
                }
            }
        };

        try
        {
            checkConnectionAndRunAndMonitor( runnable, monitor );
        }
        catch ( Exception e )
        {
            monitor.reportError( e );
        }

        if ( runnable.isCanceled() )
        {
            monitor.setCanceled( true );
        }
        if ( runnable.getException() != null )
        {
            monitor.reportError( runnable.getException() );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void renameEntry( final Dn oldDn, final Dn newDn, final boolean deleteOldRdn,
        final Control[] controls, final StudioProgressMonitor monitor, final ReferralsInfo referralsInfo )
    {
        if ( connection.isReadOnly() )
        {
            monitor
                .reportError(
                    new Exception( NLS.bind( Messages.error__connection_is_readonly, connection.getName() ) ) );
            return;
        }

        InnerRunnable runnable = new InnerRunnable()
        {
            public void run()
            {
                try
                {
                    // Preparing the rename request
                    ModifyDnRequest request = new ModifyDnRequestImpl();
                    request.setName( oldDn );
                    request.setDeleteOldRdn( deleteOldRdn );
                    request.setNewRdn( newDn.getRdn() );
                    request.setNewSuperior( newDn.getParent() );
                    if ( controls != null )
                    {
                        request.addAllControls( controls );
                    }

                    // Performing the rename operation
                    ModifyDnResponse modifyDnResponse = ldapConnection.modifyDn( request );

                    // Handle referral
                    ReferralHandlingDataConsumer consumer = referralHandlingData -> referralHandlingData.connectionWrapper
                        .renameEntry( oldDn, newDn, deleteOldRdn, controls,
                            monitor, referralHandlingData.newReferralsInfo );

                    if ( checkAndHandleReferral( modifyDnResponse, monitor, referralsInfo, consumer ) )
                    {
                        return;
                    }

                    // Checking the response
                    checkResponse( modifyDnResponse );
                }
                catch ( Exception e )
                {
                    exception = toStudioLdapException( e );
                }

                for ( ILdapLogger logger : getLdapLoggers() )
                {
                    logger.logChangetypeModDn( connection, oldDn, newDn, deleteOldRdn, controls, exception );
                }
            }
        };

        try
        {
            checkConnectionAndRunAndMonitor( runnable, monitor );
        }
        catch ( Exception e )
        {
            monitor.reportError( e );
        }

        if ( runnable.isCanceled() )
        {
            monitor.setCanceled( true );
        }
        if ( runnable.getException() != null )
        {
            monitor.reportError( runnable.getException() );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void createEntry( final Entry entry, final Control[] controls,
        final StudioProgressMonitor monitor, final ReferralsInfo referralsInfo )
    {
        if ( connection.isReadOnly() )
        {
            monitor
                .reportError(
                    new Exception( NLS.bind( Messages.error__connection_is_readonly, connection.getName() ) ) );
            return;
        }

        InnerRunnable runnable = new InnerRunnable()
        {
            public void run()
            {
                try
                {
                    // Preparing the add request
                    AddRequest request = new AddRequestImpl();
                    request.setEntry( entry );
                    if ( controls != null )
                    {
                        request.addAllControls( controls );
                    }

                    // Performing the add operation
                    AddResponse addResponse = ldapConnection.add( request );

                    // Handle referral
                    ReferralHandlingDataConsumer consumer = referralHandlingData -> {
                        Entry entryWithReferralDn = entry.clone();
                        entryWithReferralDn.setDn( referralHandlingData.referralDn );
                        referralHandlingData.connectionWrapper.createEntry( entryWithReferralDn,
                            controls, monitor, referralHandlingData.newReferralsInfo );
                    };

                    if ( checkAndHandleReferral( addResponse, monitor, referralsInfo, consumer ) )
                    {
                        return;
                    }

                    // Checking the response
                    checkResponse( addResponse );
                }
                catch ( Exception e )
                {
                    exception = toStudioLdapException( e );
                }

                for ( ILdapLogger logger : getLdapLoggers() )
                {
                    logger.logChangetypeAdd( connection, entry, controls, exception );
                }
            }
        };

        try
        {
            checkConnectionAndRunAndMonitor( runnable, monitor );
        }
        catch ( Exception e )
        {
            monitor.reportError( e );
        }

        if ( runnable.isCanceled() )
        {
            monitor.setCanceled( true );
        }
        if ( runnable.getException() != null )
        {
            monitor.reportError( runnable.getException() );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void deleteEntry( final Dn dn, final Control[] controls, final StudioProgressMonitor monitor,
        final ReferralsInfo referralsInfo )
    {
        if ( connection.isReadOnly() )
        {
            monitor
                .reportError(
                    new Exception( NLS.bind( Messages.error__connection_is_readonly, connection.getName() ) ) );
            return;
        }

        InnerRunnable runnable = new InnerRunnable()
        {
            public void run()
            {
                try
                {
                    // Preparing the delete request
                    DeleteRequest request = new DeleteRequestImpl();
                    request.setName( dn );
                    if ( controls != null )
                    {
                        request.addAllControls( controls );
                    }

                    // Performing the delete operation
                    DeleteResponse deleteResponse = ldapConnection.delete( request );

                    // Handle referral
                    ReferralHandlingDataConsumer consumer = referralHandlingData -> referralHandlingData.connectionWrapper
                        .deleteEntry( new Dn( referralHandlingData.referralDn ), controls, monitor,
                            referralHandlingData.newReferralsInfo );

                    if ( checkAndHandleReferral( deleteResponse, monitor, referralsInfo, consumer ) )
                    {
                        return;
                    }

                    // Checking the response
                    checkResponse( deleteResponse );
                }
                catch ( Exception e )
                {
                    exception = toStudioLdapException( e );
                }

                for ( ILdapLogger logger : getLdapLoggers() )
                {
                    logger.logChangetypeDelete( connection, dn, controls, exception );
                }
            }
        };

        try
        {
            checkConnectionAndRunAndMonitor( runnable, monitor );
        }
        catch ( Exception e )
        {
            monitor.reportError( e );
        }

        if ( runnable.isCanceled() )
        {
            monitor.setCanceled( true );
        }
        if ( runnable.getException() != null )
        {
            monitor.reportError( runnable.getException() );
        }
    }


    @Override
    public ExtendedResponse extended( ExtendedRequest request, StudioProgressMonitor monitor )
    {
        if ( connection.isReadOnly() )
        {
            monitor
                .reportError(
                    new Exception( NLS.bind( Messages.error__connection_is_readonly, connection.getName() ) ) );
            return null;
        }

        ExtendedResponse[] outerResponse = new ExtendedResponse[1];
        InnerRunnable runnable = new InnerRunnable()
        {
            public void run()
            {
                try
                {
                    ExtendedResponse response = ldapConnection.extended( request );
                    outerResponse[0] = response;

                    // TODO: handle referrals?

                    // Checking the response
                    checkResponse( response );
                }
                catch ( Exception e )
                {
                    exception = toStudioLdapException( e );
                }

                for ( ILdapLogger logger : getLdapLoggers() )
                {
                }
            }
        };

        try
        {
            checkConnectionAndRunAndMonitor( runnable, monitor );
        }
        catch ( Exception e )
        {
            monitor.reportError( e );
        }

        if ( runnable.isCanceled() )
        {
            monitor.setCanceled( true );
        }
        if ( runnable.getException() != null )
        {
            monitor.reportError( runnable.getException() );
        }

        return outerResponse[0];
    }

    /**
     * Inner runnable used in connection wrapper operations.
     *
     * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
     */
    abstract class InnerRunnable implements Runnable
    {
        protected StudioSearchResultEnumeration searchResultEnumeration = null;
        protected StudioLdapException exception = null;
        protected boolean canceled = false;

        /**
         * Gets the exception.
         * 
         * @return the exception
         */
        public Exception getException()
        {
            return exception;
        }


        /**
         * Gets the result.
         * 
         * @return the result
         */
        public StudioSearchResultEnumeration getResult()
        {
            return searchResultEnumeration;
        }


        /**
         * Checks if is canceled.
         * 
         * @return true, if is canceled
         */
        public boolean isCanceled()
        {
            return canceled;
        }


        /**
         * Reset.
         */
        public void reset()
        {
            searchResultEnumeration = null;
            exception = null;
            canceled = false;
        }
    }

    @FunctionalInterface
    private interface ReferralHandlingDataConsumer
    {

        void accept( ReferralHandlingData t ) throws LdapException;

    }

    private boolean checkAndHandleReferral( ResultResponse response, StudioProgressMonitor monitor,
        ReferralsInfo referralsInfo, ReferralHandlingDataConsumer consumer ) throws LdapException
    {
        if ( response == null )
        {
            return false;
        }

        LdapResult ldapResult = response.getLdapResult();
        if ( ldapResult == null || !ResultCodeEnum.REFERRAL.equals( ldapResult.getResultCode() ) )
        {
            return false;
        }

        if ( referralsInfo == null )
        {
            referralsInfo = new ReferralsInfo( true );
        }

        Referral referral = ldapResult.getReferral();
        referralsInfo.addReferral( referral );
        Referral nextReferral = referralsInfo.getNextReferral();

        Connection referralConnection = ConnectionWrapperUtils.getReferralConnection( nextReferral, monitor, this );
        if ( referralConnection == null )
        {
            monitor.setCanceled( true );
            return true;
        }

        List<String> urls = new ArrayList<>( referral.getLdapUrls() );
        String referralDn = new LdapUrl( urls.get( 0 ) ).getDn().getName();
        ReferralHandlingData referralHandlingData = new ReferralHandlingData( referralConnection.getConnectionWrapper(),
            referralDn, referralsInfo );
        consumer.accept( referralHandlingData );

        return true;
    }

    static class ReferralHandlingData
    {
        ConnectionWrapper connectionWrapper;
        String referralDn;
        ReferralsInfo newReferralsInfo;

        ReferralHandlingData( ConnectionWrapper connectionWrapper, String referralDn, ReferralsInfo newReferralsInfo )
        {
            this.connectionWrapper = connectionWrapper;
            this.referralDn = referralDn;
            this.newReferralsInfo = newReferralsInfo;
        }
    }

    private void checkConnectionAndRunAndMonitor( final InnerRunnable runnable, final StudioProgressMonitor monitor )
        throws Exception
    {
        // check connection
        if ( !isConnected() )
        {
            doConnect( monitor );
            doBind( monitor );
        }
        if ( ldapConnection == null )
        {
            throw new InvalidConnectionException( Messages.DirectoryApiConnectionWrapper_NoConnection );
        }

        // loop for reconnection
        for ( int i = 0; i <= 1; i++ )
        {
            runAndMonitor( runnable, monitor );

            // check reconnection
            if ( ( i == 0 ) && ( runnable.getException() instanceof InvalidConnectionException ) )
            {
                doConnect( monitor );
                doBind( monitor );
                runnable.reset();
            }
            else
            {
                break;
            }
        }
    }


    private void runAndMonitor( final InnerRunnable runnable, final StudioProgressMonitor monitor )
        throws CancelException
    {
        if ( !monitor.isCanceled() )
        {
            // monitor
            StudioProgressMonitor.CancelListener listener = event -> {
                if ( monitor.isCanceled() )
                {
                    if ( jobThread != null && jobThread.isAlive() )
                    {
                        jobThread.interrupt();
                    }

                    if ( ldapConnection != null )
                    {
                        try
                        {
                            ldapConnection.close();
                        }
                        catch ( Exception e )
                        {
                        }

                        ldapConnection = null;
                    }
                }
            };

            monitor.addCancelListener( listener );
            jobThread = Thread.currentThread();

            // run
            try
            {
                runnable.run();
            }
            finally
            {
                monitor.removeCancelListener( listener );
                jobThread = null;
            }

            if ( monitor.isCanceled() )
            {
                throw new CancelException();
            }
        }
    }

    private final class InnerConfiguration extends Configuration
    {
        private String krb5LoginModule;
        private AppConfigurationEntry[] configList = null;

        public InnerConfiguration( String krb5LoginModule )
        {
            this.krb5LoginModule = krb5LoginModule;
        }


        public AppConfigurationEntry[] getAppConfigurationEntry( String applicationName )
        {
            if ( configList == null )
            {
                HashMap<String, Object> options = new HashMap<>();

                // TODO: this only works for Sun JVM
                options.put( "refreshKrb5Config", "true" ); //$NON-NLS-1$ //$NON-NLS-2$
                switch ( connection.getConnectionParameter().getKrb5CredentialConfiguration() )
                {
                    case USE_NATIVE:
                        options.put( "useTicketCache", "true" ); //$NON-NLS-1$ //$NON-NLS-2$
                        options.put( "doNotPrompt", "true" ); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                    case OBTAIN_TGT:
                        options.put( "doNotPrompt", "false" ); //$NON-NLS-1$ //$NON-NLS-2$
                        break;
                }

                configList = new AppConfigurationEntry[1];
                configList[0] = new AppConfigurationEntry( krb5LoginModule, LoginModuleControlFlag.REQUIRED, options );
            }
            return configList;
        }


        @Override
        public void refresh()
        {
        }
    }

    private List<ILdapLogger> getLdapLoggers()
    {
        return ConnectionCorePlugin.getDefault().getLdapLoggers();
    }


    /**
     * Checks the given response.
     *
     * @param response
     *      the response
     * @throws Exception
     *      if the LDAP result associated with the response is not a success
     */
    private void checkResponse( ResultResponse response ) throws Exception
    {
        if ( response != null )
        {
            ResultCodeEnum.processResponse( response );
        }
    }


    private StudioLdapException toStudioLdapException( Exception exception )
    {
        if ( exception == null )
        {
            return null;
        }
        else if ( exception instanceof LdapException )
        {
            return new StudioLdapException( ( LdapException ) exception );
        }
        else
        {
            return new StudioLdapException( exception );
        }
    }

}
