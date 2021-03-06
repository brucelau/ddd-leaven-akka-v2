package ecommerce.invoicing

import ecommerce.sales._
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.eventhandling.EventPublisher
import pl.newicom.dddd.office.LocalOfficeId
import pl.newicom.dddd.office.LocalOfficeId.fromRemoteId

object Invoice extends AggregateRootSupport {

  sealed trait Invoicing extends AggregateActions[Event, Invoicing]

  implicit case object Uninitialized extends Invoicing with Uninitialized[Invoicing] {

    def actions: Actions =
      handleCommands {
        case CreateInvoice(invoiceId, orderId, customerId, totalAmount, createEpoch) =>
          InvoiceCreated(invoiceId, orderId, customerId, totalAmount, createEpoch)
      }
      .handleEvents {
        case InvoiceCreated(_, _, _, _, _) =>
          Active(amountPaid = None)
      }
  }

 case class Active(amountPaid: Option[Money]) extends Invoicing {

   def actions: Actions =
     handleCommands {
       case ReceivePayment(invoiceId, orderId, amount, paymentId) =>
         OrderBilled(invoiceId, orderId, amount, paymentId)

       case CancelInvoice(invoiceId, orderId) =>
         OrderBillingFailed(invoiceId, orderId)
     }
     .handleEvents {
        case OrderBilled(_, _, amount, _) =>
          copy(amountPaid = Some(amountPaid.getOrElse(Money()) + amount))
        case OrderBillingFailed(_, _) =>
          this
      }
  }

  implicit val officeId: LocalOfficeId[Invoice] = fromRemoteId[Invoice](InvoicingOfficeId)
}

import ecommerce.invoicing.Invoice._

abstract class Invoice(override val pc: PassivationConfig) extends AggregateRoot[Event, Invoicing, Invoice] {
  this: EventPublisher =>
}
