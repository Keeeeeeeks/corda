package net.corda.bank.api

import net.corda.bank.flow.IssuerFlow.IssuanceRequester
import net.corda.core.contracts.Amount
import net.corda.core.contracts.currency
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.loggerFor
import java.time.LocalDateTime
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// API is accessible from /api/bank. All paths specified below are relative to it.
@Path("bank")
class BankOfCordaWebApi(val rpc: CordaRPCOps) {
    data class IssueRequestParams(val amount: Long, val currency: String,
                                  val issueToPartyName: String, val issueToPartyRefAsString: String,
                                  val issuerBankName: String)
    private companion object {
        val logger = loggerFor<BankOfCordaWebApi>()
    }
    @GET
    @Path("date")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCurrentDate(): Any {
        return mapOf("date" to LocalDateTime.now().toLocalDate())
    }
    /**
     *  Request asset issuance
     */
    @POST
    @Path("issue-asset-request")
    @Consumes(MediaType.APPLICATION_JSON)
    fun issueAssetRequest(params: IssueRequestParams): Response {
        // Resolve parties via RPC
        val issueToParty = rpc.partyFromName(params.issueToPartyName)
                ?: throw Exception("Unable to locate ${params.issueToPartyName} in Network Map Service")
        val issuerBankParty = rpc.partyFromName(params.issuerBankName)
                ?: throw Exception("Unable to locate ${params.issuerBankName} in Network Map Service")

        val amount = Amount(params.amount, currency(params.currency))
        val issuerToPartyRef = OpaqueBytes.of(params.issueToPartyRefAsString.toByte())

        // invoke client side of Issuer Flow: IssuanceRequester
        // The line below blocks and waits for the future to resolve.
        val result = rpc.startFlow(::IssuanceRequester, amount, issueToParty, issuerToPartyRef, issuerBankParty).returnValue.toBlocking().first()
        if (result is SignedTransaction) {
            logger.info("Issue request completed successfully: ${params}")
            return Response.status(Response.Status.CREATED).build()
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }
    }
}