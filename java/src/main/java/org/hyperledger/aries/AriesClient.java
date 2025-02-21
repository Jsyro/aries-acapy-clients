/*
  Copyright (c) 2020 Robert Bosch GmbH. All Rights Reserved.

  SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.aries;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.aries.api.connection.*;
import org.hyperledger.aries.api.creddef.CredentialDefinition;
import org.hyperledger.aries.api.creddef.CredentialDefinition.CredentialDefinitionRequest;
import org.hyperledger.aries.api.creddef.CredentialDefinition.CredentialDefinitionResponse;
import org.hyperledger.aries.api.creddef.CredentialDefinition.CredentialDefinitionsCreated;
import org.hyperledger.aries.api.creddef.CredentialDefinitionFilter;
import org.hyperledger.aries.api.credential.*;
import org.hyperledger.aries.api.exception.AriesException;
import org.hyperledger.aries.api.jsonld.*;
import org.hyperledger.aries.api.ledger.*;
import org.hyperledger.aries.api.message.BasicMessage;
import org.hyperledger.aries.api.message.PingRequest;
import org.hyperledger.aries.api.message.PingResponse;
import org.hyperledger.aries.api.multitenancy.*;
import org.hyperledger.aries.api.proof.*;
import org.hyperledger.aries.api.revocation.*;
import org.hyperledger.aries.api.schema.SchemaSendRequest;
import org.hyperledger.aries.api.schema.SchemaSendResponse;
import org.hyperledger.aries.api.schema.SchemaSendResponse.Schema;
import org.hyperledger.aries.api.server.AdminStatusLiveliness;
import org.hyperledger.aries.api.server.AdminStatusReadiness;
import org.hyperledger.aries.api.wallet.GetDidEndpointResponse;
import org.hyperledger.aries.api.wallet.SetDidEndpointRequest;
import org.hyperledger.aries.api.wallet.WalletDidResponse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
public class AriesClient extends BaseClient {

    private final String url;
    private final String apiKey;
    private final String bearerToken;

    /**
     * @param url The aca-py admin api URL without a path e.g. protocol://host:[port]
     * @param apiKey The admin api api key
     * @param bearerToken the Bearer token used in the Authorization header when running in multi tenant mode
     * @param client {@link OkHttpClient} if null a default client is created
     */
    @Builder
    public AriesClient(@NonNull String url, @Nullable String apiKey,
                       @Nullable String bearerToken, @Nullable OkHttpClient client) {
        super(client);
        this.url = StringUtils.trim(url);
        this.apiKey = StringUtils.trimToEmpty(apiKey);
        this.bearerToken = StringUtils.trimToEmpty(bearerToken);
    }

    // ----------------------------------------------------
    // Connection - Connection Management
    // ----------------------------------------------------

    /**
     * Query agent-to-agent connections
     * @return List of agent-to-agent connections
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<ConnectionRecord>> connections() throws IOException {
        return connections(null);
    }

    /**
     * Query agent-to-agent connections
     * @param filter {@link ConnectionFilter}
     * @return List of agent-to-agent connections
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<ConnectionRecord>> connections(@Nullable ConnectionFilter filter) throws IOException {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/connections")).newBuilder();
        if (filter != null) {
            filter.buildParams(b);
        }
        Request req = buildGet(b.build().toString());
        final Optional<String> resp = raw(req);
        return getWrapped(resp, "results", CONNECTION_TYPE);
    }

    /**
     * Query agent-to-agent connections
     * @return only the connection IDs
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public List<String> connectionIds() throws IOException {
        return connectionIds(null);
    }

    /**
     * Query agent-to-agent connections
     * @param filter {@link ConnectionFilter}
     * @return only the connection IDs based on the filter criteria
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public List<String> connectionIds(@Nullable ConnectionFilter filter) throws IOException {
        List<String> result = new ArrayList<>();
        final Optional<List<ConnectionRecord>> c = connections(filter);
        if (c.isPresent()) {
            result = c.get().stream().map(ConnectionRecord::getConnectionId).collect(Collectors.toList());
        }
        return result;
    }

    /**
     * Remove an existing connection record
     * @param connectionId the connection id
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public void connectionsRemove(@NonNull String connectionId) throws IOException {
        Request req = buildDelete(url + "/connections/" + connectionId);
        call(req);
      }

    /**
     * Create a new connection invitation
     * @return {@link CreateInvitationResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @deprecated see {@link #connectionsCreateInvitation(CreateInvitationRequest)}
     */
    @Deprecated
    public Optional<CreateInvitationResponse> connectionsCreateInvitation() throws IOException {
        Request req = buildPost(url + "/connections/create-invitation", EMPTY_JSON);
        return call(req, CreateInvitationResponse.class);
    }

    /**
     * Create a new connection invitation
     * @param request {@link CreateInvitationRequest}
     * @return {@link CreateInvitationResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @since 0.6.0
     */
    public Optional<CreateInvitationResponse> connectionsCreateInvitation(@NonNull CreateInvitationRequest request)
            throws IOException {
        return connectionsCreateInvitation(request, null);
    }

    /**
     * Create a new connection invitation
     * @param request {@link CreateInvitationRequest}
     * @param params {@link CreateInvitationParams}
     * @return {@link CreateInvitationResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * @since 0.6.0
     */
    public Optional<CreateInvitationResponse> connectionsCreateInvitation(
            @NonNull CreateInvitationRequest request, @Nullable CreateInvitationParams params) throws IOException {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/connections/create-invitation")).newBuilder();
        if (params != null) {
            params.buildParams(b);
        }
        Request req = buildPost(b.build().toString(), request);
        return call(req, CreateInvitationResponse.class);
    }

    /**
     * Receive a new connection invitation
     * @param invite {@link ReceiveInvitationRequest}
     * @param alias optional: alias for the connection
     * @return {@link ConnectionRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<ConnectionRecord> connectionsReceiveInvitation(
            @NonNull ReceiveInvitationRequest invite, @Nullable String alias)
            throws IOException{
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/connections/receive-invitation")).newBuilder();
        if (StringUtils.isNotEmpty(alias)) {
            b.addQueryParameter("alias", alias);
        }
        Request req = buildPost(b.build().toString(), invite);
        return call(req, ConnectionRecord.class);
    }

    // ----------------------------------------------------
    // Basic Message - Simple Messaging
    // ----------------------------------------------------

    /**
     * Send a basic message to a connection
     * @param connectionId the connection id
     * @param msg the message
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public void connectionsSendMessage(@NonNull String connectionId, @NonNull BasicMessage msg) throws IOException {
        Request req = buildPost(url + "/connections/" + connectionId + "/send-message", msg);
        call(req);
    }

    // ----------------------------------------------------
    // Trust Ping - Trust-ping Over Connection
    // ----------------------------------------------------

    /**
     * Send a trust ping to a connection
     * @param connectionId the connection id
     * @param comment comment for the ping message
     * @return {@link PingResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<PingResponse> connectionsSendPing(@NonNull String connectionId, @NonNull PingRequest comment)
            throws IOException {
        Request req = buildPost(url + "/connections/" + connectionId + "/send-ping", comment);
        return call(req, PingResponse.class);
    }

    // ----------------------------------------------------
    // Credential Definition - Credential Definition Operations
    // ----------------------------------------------------

    /**
     * Sends a credential definition to the ledger
     * @param defReq {@link CredentialDefinitionRequest}
     * @return {@link CredentialDefinitionResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<CredentialDefinitionResponse> credentialDefinitionsCreate(
            @NonNull CredentialDefinitionRequest defReq) throws IOException {
        Request req = buildPost(url + "/credential-definitions", defReq);
        return call(req, CredentialDefinitionResponse.class);
    }

    /**
     * Search for matching credential definitions that originated from this agent
     * @param filter {@link CredentialDefinitionFilter}
     * @return {@link CredentialDefinitionsCreated}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<CredentialDefinitionsCreated> credentialDefinitionsCreated(
            @Nullable CredentialDefinitionFilter filter) throws IOException {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/credential-definitions/created")).newBuilder();
        if (filter != null) {
            filter.buildParams(b);
        }
        Request req = buildGet(b.build().toString());
        return call(req , CredentialDefinitionsCreated.class);
    }

    /**
     * Gets a credential definition from the ledger
     * @param id credential definition id
     * @return {@link CredentialDefinition}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<CredentialDefinition> credentialDefinitionsGetById(@NonNull String id) throws IOException {
        Request req = buildGet(url + "/credential-definitions/" + id);
        final Optional<String> resp = raw(req);
        return getWrapped(resp, "credential_definition", CredentialDefinition.class);
    }

    // ----------------------------------------------------
    // Issue Credential - Credential Issue v1.0
    // ----------------------------------------------------

    /**
     * Fetch all credential exchange records
     * @param filter {@link IssueCredentialFilter}
     * @return list of {@link CredentialExchange}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<CredentialExchange>> issueCredentialRecords(IssueCredentialFilter filter) throws IOException {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/issue-credential/records")).newBuilder();
        if (filter != null) {
            filter.buildParams(b);
        }
        Request req = buildGet(b.build().toString());
        final Optional<String> resp = raw(req);
        return getWrapped(resp, "results", ISSUE_CREDENTIAL_TYPE);
    }

    /**
     * Send holder a credential, automating the entire flow
     * @param proposalRequest {@link CredentialProposalRequest} the credential to be issued
     * @return {@link CredentialExchange}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<CredentialExchange> issueCredentialSend(@NonNull CredentialProposalRequest proposalRequest)
            throws IOException {
        Request req = buildPost(url + "/issue-credential/send", proposalRequest);
        return call(req, CredentialExchange.class);
    }

    /**
     * Send issuer a credential proposal
     * @param proposalRequest {@link CredentialProposalRequest} the requested credential
     * @return {@link CredentialExchange}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<CredentialExchange> issueCredentialSendProposal(@NonNull CredentialProposalRequest proposalRequest)
            throws IOException {
        Request req = buildPost(url + "/issue-credential/send-proposal", proposalRequest);
        return call(req, CredentialExchange.class);
    }

    /**
     * Store a received credential
     * @param credentialExchangeId the credential exchange id
     * @return {@link CredentialExchange}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<CredentialExchange> issueCredentialRecordsStore(@NonNull String credentialExchangeId)
            throws IOException {
        Request req = buildPost(url + "/issue-credential/records/" + credentialExchangeId + "/store", "");
        return call(req, CredentialExchange.class);
    }

    /**
     * Remove an existing credential exchange record
     * @param credentialExchangeId the credential exchange id
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public void issueCredentialRecordsRemove(@NonNull String credentialExchangeId) throws IOException {
        Request req = buildDelete(url + "/issue-credential/records/" + credentialExchangeId);
        call(req);
      }

    // ----------------------------------------------------
    // Issue Credential - Credential Issue v2.0
    // ----------------------------------------------------

    // TODO

    // ----------------------------------------------------
    // Credentials- Holder Credential Management
    // ----------------------------------------------------

    /**
     * Fetch credentials from wallet
     * @return list of credentials {@link Credential}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<Credential>> credentials() throws IOException {
        return credentials(null);
    }

    /**
     * Fetch credentials from wallet
     * @param filter see {@link CredentialFilter} for prepared filters
     * @return Credentials that match the filter criteria
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<Credential>> credentials(@Nullable Predicate<Credential> filter) throws IOException {
        Optional<List<Credential>> result = Optional.empty();
        Request req = buildGet(url + "/credentials");
        final Optional<String> resp = raw(req);
        if (resp.isPresent()) {
            result = getWrapped(resp, "results", CREDENTIAL_TYPE);
            if (result.isPresent() && filter != null) {
                result = Optional.of(result.get().stream().filter(filter).collect(Collectors.toList()));
            }
        }
        return result;
    }

    /**
     * Fetch credentials ids from wallet
     * @return only the credential IDs
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public List<String> credentialIds() throws IOException {
        return credentialIds(null);
    }

    /**
     * Fetch credentials ids from wallet
     * @param filter see {@link CredentialFilter} for prepared filters
     * @return only the credential IDs based on the filter criteria
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public List<String> credentialIds(@Nullable Predicate<Credential> filter) throws IOException {
        List<String> result = new ArrayList<>();
        final Optional<List<Credential>> c = credentials(filter);
        if (c.isPresent()) {
            result = c.get().stream().map(Credential::getReferent).collect(Collectors.toList());
        }
        return result;
    }

    /**
     * Fetch a credential from wallet by id
     * @param credentialId credentialId
     * @return {@link Credential}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<Credential> credential(@NonNull String credentialId) throws IOException {
        Request req = buildGet(url + "/credential/" + credentialId);
        return call(req, Credential.class);
    }

    /**
     * Remove a credential from the wallet by id (credentialId)
     * @param credentialId credentialId
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public void credentialRemove(@NonNull String credentialId) throws IOException {
        Request req = buildDelete(url + "/credential/" + credentialId);
        call(req);
    }

    /**
     * Query credential revocation status by id
     * @param credentialId credentialId
     * @return {@link Credential.CredentialRevokedResult}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<Credential.CredentialRevokedResult> credentialRevoked(@NonNull String credentialId)
            throws IOException {
        Request req = buildGet(url + "/credential/revoked/" + credentialId);
        return call(req, Credential.CredentialRevokedResult.class);
    }

    // ----------------------------------------------------
    // Multitenancy - Multitenant wallet management
    // ----------------------------------------------------

    /**
     * Create sub wallet
     * @param request {@link CreateWalletRequest}
     * @return {@link WalletRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<WalletRecord> multitenancyWalletCreate(@NonNull CreateWalletRequest request) throws IOException {
        Request req = buildPost(url + "/multitenancy/wallet", request);
        return call(req, WalletRecord.class);
    }

    /**
     * Get a singe sub wallet
     * @param walletId sub wallet identifier
     * @return {@link WalletRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<WalletRecord> multitenancyWalletGet(@NonNull String walletId)
            throws IOException {
        Request req = buildGet(url + "/multitenancy/wallet/" + walletId);
        return call(req, WalletRecord.class);
    }

    /**
     * Update a sub wallet
     * @param walletId sub wallet identifier
     * @param request {@link UpdateWalletRequest}
     * @return {@link WalletRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<WalletRecord> multitenancyWalletUpdate(@NonNull String walletId,
           @NonNull UpdateWalletRequest request) throws IOException {
        Request req = buildPut(url + "/multitenancy/wallet/" + walletId, request);
        return call(req, WalletRecord.class);
    }

    /**
     * remove a sub wallet
     * @param walletId sub wallet identifier
     * @param request {@link RemoveWalletRequest}
     * @return no body on success
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<Void> multitenancyWalletRemove(@NonNull String walletId,
        @NonNull RemoveWalletRequest request) throws IOException {
        Request req = buildPost(url + "/multitenancy/wallet/" + walletId + "/remove", request);
        return call(req, Void.class);
    }

    /**
     * Get auth token for a sub wallet
     * @param walletId sub wallet identifier
     * @param request {@link CreateWalletTokenRequest}
     * @return {@link CreateWalletTokenResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<CreateWalletTokenResponse> multitenancyWalletToken(@NonNull String walletId,
        @NonNull CreateWalletTokenRequest request) throws IOException {
        Request req = buildPost(url + "/multitenancy/wallet/" + walletId + "/token", request);
        return call(req, CreateWalletTokenResponse.class);
    }

    /**
     * Query sub wallets
     * @param walletName optional the wallets name
     * @return list of {@link WalletRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<WalletRecord>> multitenancyWallets(String walletName)
            throws IOException {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/multitenancy/wallets")).newBuilder();
        if (StringUtils.isNotEmpty(walletName)) {
            b.addQueryParameter("wallet_name", walletName);
        }
        Request req = buildGet(b.build().toString());
        final Optional<String> resp = raw(req);
        return getWrapped(resp, "results", WALLET_RECORD_TYPE);
    }

    // ----------------------------------------------------
    // Present Proof - Proof Presentation
    // ----------------------------------------------------

    /**
     * Fetch all present-proof exchange records
     * @return list of {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<PresentationExchangeRecord>> presentProofRecords() throws IOException {
        return presentProofRecords(null);
    }

    /**
     * Fetch all present-proof exchange records
     * @param filter {@link PresentProofRecordsFilter}
     * @return list of {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<PresentationExchangeRecord>> presentProofRecords(@Nullable PresentProofRecordsFilter filter)
            throws IOException {
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/present-proof/records")).newBuilder();
        if (filter != null) {
            filter.buildParams(b);
        }
        Request req = buildGet(b.build().toString());
        final Optional<String> resp = raw(req);
        return getWrapped(resp, "results", PROOF_TYPE);
    }

    /**
     * Fetch a single presentation exchange record by ID
     * @param presentationExchangeId the presentation exchange id
     * @return {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<PresentationExchangeRecord> presentProofRecord(@NonNull String presentationExchangeId)
            throws IOException {
        Request req = buildGet(url + "/present-proof/records/" + presentationExchangeId);
        return call(req, PresentationExchangeRecord.class);
    }

    /**
     * Fetch credentials for a presentation request from wallet
     * @param presentationExchangeId the presentation exchange id
     * @return {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<PresentationRequestCredential>> presentProofRecordCredentials(@NonNull String presentationExchangeId)
            throws IOException {
        Request req = buildGet(url + "/present-proof/records/" + presentationExchangeId + "/credentials");
        //not sure how to expect list
        return call(req, List<PresentationRequestCredential>.class);
    }


    /**
     * Sends a presentation proposal
     * @param proofProposal {@link PresentProofProposal}
     * @return {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<PresentationExchangeRecord> presentProofSendProposal(@NonNull PresentProofProposal proofProposal)
            throws IOException{
        Request req = buildPost(url + "/present-proof/send-proposal", proofProposal);
        return call(req, PresentationExchangeRecord.class);
    }

    /**
     * Creates a presentation request not bound to any proposal or existing connection
     * @param proofRequest {@link PresentProofRequest}
     * @return {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<PresentationExchangeRecord> presentProofCreateRequest(@NonNull PresentProofRequest proofRequest)
            throws IOException {
        Request req = buildPost(url + "/present-proof/create-request", proofRequest);
        return call(req, PresentationExchangeRecord.class);
    }

    /**
     * Sends a free presentation request not bound to any proposal
     * @param proofRequest {@link PresentProofRequest}
     * @return {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<PresentationExchangeRecord> presentProofSendRequest(@NonNull PresentProofRequest proofRequest)
            throws IOException {
        Request req = buildPost(url + "/present-proof/send-request", proofRequest);
        return call(req, PresentationExchangeRecord.class);
    }

    /**
     * Sends a free presentation request not bound to any proposal. Use this method if you want to have full
     * control over the proof request.
     * @param proofRequestJson json string
     * @return {@link PresentationExchangeRecord}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<PresentationExchangeRecord> presentProofSendRequest(@NonNull String proofRequestJson)
            throws IOException {
        JsonObject proofRequest = gson.fromJson(proofRequestJson, JsonObject.class);
        Request req = buildPost(url + "/present-proof/send-request", proofRequest);
        return call(req, PresentationExchangeRecord.class);
    }

    /**
     * Sends a proof presentation
     * @param presentationExchangeId the presentation exchange id
     * @param presentationRequest {@link PresentationRequest}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public void presentProofRecordsSendPresentation(@NonNull String presentationExchangeId,
            @NonNull PresentationRequest presentationRequest) throws IOException {
        Request req = buildPost(url + "/present-proof/records/" + presentationExchangeId + "/send-presentation",
                presentationRequest);
        call(req);
    }

    /**
     * Remove an existing presentation exchange record by ID
     * @param presentationExchangeId the presentation exchange id
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public void presentProofRecordsRemove(@NonNull String presentationExchangeId) throws IOException {
        Request req = buildDelete(url + "/present-proof/records/" + presentationExchangeId);
        call(req);
    }

    // ----------------------------------------------------
    // Schemas
    // ----------------------------------------------------

    /**
     * Sends a schema to the ledger
     * @param schema {@link SchemaSendRequest}
     * @return {@link SchemaSendResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<SchemaSendResponse> schemas(@NonNull SchemaSendRequest schema) throws IOException {
        Request req = buildPost(url + "/schemas", schema);
        return call(req, SchemaSendResponse.class);
    }

    /**
     * Gets a schema from the ledger
     * @param schemaId the schemas id or sequence number
     * @return {@link Schema}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<Schema> schemasGetById(@NonNull String schemaId) throws IOException {
        Request req = buildGet(url + "/schemas/" + schemaId);
        return getWrapped(raw(req), "schema", Schema.class);
    }

    // ----------------------------------------------------
    // Wallet
    // ----------------------------------------------------

    /**
     * List wallet DIDs
     * @return list of {@link WalletDidResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<List<WalletDidResponse>> walletDid() throws IOException {
        Request req = buildGet(url + "/wallet/did");
        return getWrapped(raw(req), "results", WALLET_DID_TYPE);
    }

    /**
     * Create local DID
     * @return {@link WalletDidResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<WalletDidResponse> walletDidCreate() throws IOException {
        Request req = buildPost(url + "/wallet/did/create", EMPTY_JSON);
        return getWrapped(raw(req), "result", WalletDidResponse.class);
    }

    /**
     * Fetch the current public DID
     * @return {@link WalletDidResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<WalletDidResponse> walletDidPublic() throws IOException {
        Request req = buildGet(url + "/wallet/did/public");
        return getWrapped(raw(req), "result", WalletDidResponse.class);
    }

    /**
     * Update end point in wallet and, if public, on ledger
     * @param endpointRequest {@link SetDidEndpointRequest}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public void walletSetDidEndpoint(@NonNull SetDidEndpointRequest endpointRequest) throws IOException {
        Request req = buildPost(url + "/wallet/set-did-endpoint", endpointRequest);
        call(req);
    }

    /**
     * Query DID end point in wallet
     * @param did the did
     * @return {@link GetDidEndpointResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<GetDidEndpointResponse> walletGetDidEndpoint(@NonNull String did) throws IOException {
        Request req = buildGet(url + "/wallet/get-did-endpoint" + "?did=" + did);
        return call(req, GetDidEndpointResponse.class);
    }

    // ----------------------------------------------------
    // JSON-LD
    // ----------------------------------------------------

    /**
     * Sign a JSON-LD structure and return it
     * @since aca-py 0.5.2
     * @param <T> class type either {@link VerifiableCredential} or {@link VerifiablePresentation}
     * @param signRequest {@link SignRequest}
     * @param t class type either {@link VerifiableCredential} or {@link VerifiablePresentation}
     * @return either {@link VerifiableCredential} or {@link VerifiablePresentation} with {@link Proof}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public <T> Optional<T> jsonldSign(@NonNull SignRequest signRequest, @NonNull Type t) throws IOException {
        Request req = buildPost(url + "/jsonld/sign", signRequest);
        final Optional<String> raw = raw(req);
        checkForError(raw);
        return getWrapped(raw, "signed_doc", t);
    }

    /**
     * Verify a JSON-LD structure
     * @since aca-py 0.5.2
     * @param verkey the verkey
     * @param t instance to verify either {@link VerifiableCredential} or {@link VerifiablePresentation}
     * @return {@link VerifyResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<VerifyResponse> jsonldVerify(@NonNull String verkey, @NonNull Object t) throws IOException {
        if (t instanceof VerifiableCredential || t instanceof VerifiablePresentation) {
            final JsonElement jsonTree = gson.toJsonTree(t, t.getClass());
            Request req = buildPost(url + "/jsonld/verify", new VerifyRequest(verkey, jsonTree.getAsJsonObject()));
            return call(req, VerifyResponse.class);
        }
        throw new IllegalStateException("Expecting either VerifiableCredential or VerifiablePresentation");
    }

    // ----------------------------------------------------
    // Ledger
    // ----------------------------------------------------

    /**
     * Get the verkey for a did from the ledger
     * @param did the DID of interest
     * @return {@link EndpointResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<DidVerkeyResponse> ledgerDidVerkey(@NonNull String did)  throws IOException{
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/ledger/did-verkey")).newBuilder();
        b.addQueryParameter("did", did);
        Request req = buildGet(b.build().toString());
        return call(req, DidVerkeyResponse.class);
    }

    /**
     * Get the endpoint for a DID from the ledger.
     * @param did the DID of interest
     * @param type optional, endpoint type of interest (defaults to 'endpoint')
     * @return {@link EndpointResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<EndpointResponse> ledgerDidEndpoint(@NonNull String did, @Nullable EndpointType type)
            throws IOException{
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/ledger/did-endpoint")).newBuilder();
        b.addQueryParameter("did", did);
        if (type != null) {
            b.addQueryParameter("endpoint_type", type.toString());
        }
        Request req = buildGet(b.build().toString());
        return call(req, EndpointResponse.class);
    }

    /**
     * Fetch the current transaction author agreement, if any
     * @return the current transaction author agreement, if any
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<TAAInfo> ledgerTaa() throws IOException {
        Request req = buildGet(url + "/ledger/taa");
        return getWrapped(raw(req), "result", TAAInfo.class);
    }

    /**
     * Accept the transaction author agreement
     * @param taaAccept {@link TAAAccept}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     * Or AriesException if TAA is not available
     */
    public void ledgerTaaAccept(@NonNull TAAAccept taaAccept) throws IOException {
        Request req = buildPost(url + "/ledger/taa/accept", taaAccept);
        call(req);
    }

    // ----------------------------------------------------
    // Revocation
    // ----------------------------------------------------

    /**
     * Get an active revocation registry by credential definition id
     * @param credentialDefinitionId the credential definition id
     * @return {@link RevRegCreateResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<RevRegCreateResponse> revocationActiveRegistry(@NonNull String credentialDefinitionId)
            throws IOException {
        Request req = buildGet(url + "/revocation/active-registry/" + credentialDefinitionId);
        return getWrapped(raw(req), "result", RevRegCreateResponse.class);
    }

    /**
     * Creates a new revocation registry
     * Creating a new registry is a three step flow:
     * First: create the registry
     * Second: publish the URI of the tails file {@link #revocationRegistryUpdateUri}
     * Third: Set the registry to active {@link #revocationActiveRegistry}
     * @param revRegRequest {@link RevRegCreateRequest}
     * @return {@link RevRegCreateResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<RevRegCreateResponse> revocationCreateRegistry(@NonNull RevRegCreateRequest revRegRequest)
            throws IOException {
        Request req = buildPost(url + "/revocation/create-registry", revRegRequest);
        return getWrapped(raw(req), "result", RevRegCreateResponse.class);
    }

    /**
     * Search for matching revocation registries that current agent created
     * @param credentialDefinitionId the credential definition id
     * @param state {@link RevocationRegistryState}
     * @return {@link RevRegsCreated}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<RevRegsCreated> revocationRegistriesCreated(
            @Nullable String credentialDefinitionId, @Nullable RevocationRegistryState state)
            throws IOException{
        HttpUrl.Builder b = Objects.requireNonNull(HttpUrl.parse(url + "/revocation/registries/created")).newBuilder();
        if (StringUtils.isNotEmpty(credentialDefinitionId)) {
            b.addQueryParameter("cred_def_id", credentialDefinitionId);
        }
        if (state != null) {
            b.addQueryParameter("state", state.toString());
        }
        Request req = buildGet(b.build().toString());
        return call(req, RevRegsCreated.class);
    }

    /**
     * Gets revocation registry by revocation registry id
     * @param revRegId the revocation registry id
     * @return {@link RevRegCreateResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<RevRegCreateResponse> revocationRegistryGetById(@NonNull String revRegId)
            throws IOException {
        Request req = buildGet(url + "/revocation/registry/" + revRegId);
        return getWrapped(raw(req), "result", RevRegCreateResponse.class);
    }

    /**
     * Update revocation registry with new public URI to the tails file.
     * @param revRegId the revocation registry id
     * @param tailsFileUri {@link RevRegUpdateTailsFileUri} the URI of the tails file
     * @return {@link RevRegCreateResponse}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<RevRegCreateResponse> revocationRegistryUpdateUri(
            @NonNull String revRegId, @NonNull RevRegUpdateTailsFileUri tailsFileUri)
            throws IOException {
        Request req = buildPatch(url + "/revocation/registry/" + revRegId, tailsFileUri);
        return getWrapped(raw(req), "result", RevRegCreateResponse.class);
    }

    /**
     * Revoke an issued credential
     * @param revokeRequest {@link RevokeRequest}
     * @return empty object when success
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<RevRegCreateResponse.RevocationModuleResponse> revocationRevoke(@NonNull RevokeRequest revokeRequest)
            throws IOException {
        Request req = buildPost(url + "/revocation/revoke", revokeRequest);
        return call(req, RevRegCreateResponse.RevocationModuleResponse.class);
    }

    // ----------------------------------------------------
    // Server
    // ----------------------------------------------------

    /**
     * Server liveliness check
     * @since aca-py 0.5.3
     * @return {@link AdminStatusLiveliness}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<AdminStatusLiveliness> statusLive() throws IOException {
        Request req = buildGet(url + "/status/live");
        return call(req, AdminStatusLiveliness.class);
    }

    /**
     * Server readiness check
     * @since aca-py 0.5.3
     * @return {@link AdminStatusReadiness}
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    public Optional<AdminStatusReadiness> statusReady() throws IOException {
        Request req = buildGet(url + "/status/ready");
        return call(req, AdminStatusReadiness.class);
    }

    /**
     * Helper that blocks until either a timeout is reached or aca-py returns that it is ready
     * @since aca-py 0.5.3
     * @param timeout {@link Duration} how long to wait for aca-py to be ready until failing
     */
    public void statusWaitUntilReady(@NonNull Duration timeout) {
        Instant to = Instant.now().plus(timeout);
        while(Instant.now().isBefore(to)) {
            try {
                final Optional<AdminStatusReadiness> statusReady = this.statusReady();
                if (statusReady.isPresent() && statusReady.get().isReady()) {
                    log.info("aca-py is ready");
                    return;
                }
            } catch (IOException e) {
                log.trace("aca-py not ready yet, reason: {}", e.getMessage());
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for aca-py", e);
            }
        }
        String msg = "Timeout exceeded, aca-py not ready after: " + timeout.toString();
        log.error(msg);
        throw new AriesException(0, msg);
    }

    // ----------------------------------------------------
    // Internal
    // ----------------------------------------------------

    private Request buildPost(String u, Object body) {
        return request(u)
                .post(jsonBody(gson.toJson(body)))
                .build();
    }

    private Request buildPut(String u, Object body) {
        return request(u)
                .put(jsonBody(gson.toJson(body)))
                .build();
    }

    private Request buildPatch(String u, Object body) {
        return request(u)
                .patch(jsonBody(gson.toJson(body)))
                .build();
    }

    private Request buildGet(String u) {
        return request(u)
                .get()
                .build();
    }

    private Request buildDelete(String u) {
        return request(u)
                .delete()
                .build();
    }

    private Request.Builder request(String u) {
        Request.Builder b = new Request.Builder()
                .url(u)
                .header(X_API_KEY, apiKey);
        if (StringUtils.isNotEmpty(bearerToken)) {
            b.header(AUTHORIZATION, BEARER + bearerToken);
        }
        return b;
    }
}
