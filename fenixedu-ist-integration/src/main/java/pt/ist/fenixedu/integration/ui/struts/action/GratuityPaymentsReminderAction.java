/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST Integration.
 *
 * FenixEdu IST Integration is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST Integration is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST Integration.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.integration.ui.struts.action;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.accounting.PaymentCode;
import org.fenixedu.academic.domain.accounting.PaymentPlan;
import org.fenixedu.academic.domain.accounting.events.gratuity.GratuityEvent;
import org.fenixedu.academic.domain.accounting.paymentCodes.InstallmentPaymentCode;
import org.fenixedu.academic.predicate.AccessControl;
import org.fenixedu.academic.ui.struts.action.base.FenixDispatchAction;
import org.fenixedu.academic.util.Money;
import org.fenixedu.bennu.struts.annotations.Forward;
import org.fenixedu.bennu.struts.annotations.Forwards;
import org.fenixedu.bennu.struts.annotations.Mapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Set;

@Mapping(path = "/gratuityPaymentsReminder")
@Forwards({

@Forward(name = "showGratuityPaymentsReminder", path = "/showGratuityPaymentsReminder.jsp")

})
public class GratuityPaymentsReminderAction extends FenixDispatchAction {

    public ActionForward showReminder(ActionMapping mapping, ActionForm form, HttpServletRequest request,
            HttpServletResponse response) {

        Person person = AccessControl.getPerson();

        InstallmentPaymentCode debtPaymentCode = readDebtPaymentCodeInThisExecutionYear(person);
        if (debtPaymentCode != null) {
            PaymentPlan paymentPlan = debtPaymentCode.getInstallment().getPaymentPlan();

            if (paymentPlan.isForPartialRegime() && debtPaymentCode.getInstallment().getOrder() == 1) {
                request.setAttribute("remnantGratuity", false);
                request.setAttribute("remainingPaymentEndDate", "15 de Dezembro de 2011");
                request.setAttribute("remainingPaymentDebt", debtPaymentCode.getMinAmount().toString());
                request.setAttribute("remainingPaymentCode", debtPaymentCode.getCode());
            } else if (debtPaymentCode.getInstallment().getOrder() == 3) {
                request.setAttribute("remnantGratuity", false);
                request.setAttribute("remainingPaymentEndDate", "31 de Maio de 2012");
                request.setAttribute("remainingPaymentDebt", debtPaymentCode.getMinAmount().toString());
                request.setAttribute("remainingPaymentCode", debtPaymentCode.getCode());
            }
        } else {
            request.setAttribute("remnantGratuity", false);
        }

        return mapping.findForward("showGratuityPaymentsReminder");
    }

    final Money THRESHOLD = new Money("13.00");

    private InstallmentPaymentCode readDebtPaymentCodeInThisExecutionYear(Person person) {
        ExecutionYear currentExecutionYear = ExecutionYear.readExecutionYearByName("2011/2012");

        Set<GratuityEvent> gratuityEvents = person.getGratuityEvents();

        return (InstallmentPaymentCode) gratuityEvents.stream()
                .filter(gratuityEvent -> gratuityEvent.getExecutionYear() == currentExecutionYear)
                .filter(gratuityEvent -> gratuityEvent.getAmountToPay().lessThan(THRESHOLD))
                .map(this::getActivePaymentCode)
                .filter(activePaymentCode -> activePaymentCode instanceof InstallmentPaymentCode)
                .findFirst().orElse(null);
    }

    private PaymentCode getActivePaymentCode(GratuityEvent gratuityEvent) {
        return gratuityEvent.getAllPaymentCodes().stream().filter(PaymentCode::isNew).findFirst().orElse(null);
    }

}