/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST Vigilancies.
 *
 * FenixEdu IST Vigilancies is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST Vigilancies is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST Vigilancies.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.vigilancies.service;

import java.util.*;

import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.WrittenEvaluation;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.bennu.core.i18n.BundleUtil;
import org.fenixedu.messaging.core.domain.Message;
import org.joda.time.DateTime;

import pt.ist.fenixedu.vigilancies.domain.ExamCoordinator;
import pt.ist.fenixedu.vigilancies.domain.VigilantGroup;
import pt.ist.fenixedu.vigilancies.domain.VigilantWrapper;
import pt.ist.fenixframework.Atomic;

public class CreateConvokes {

    @Atomic
    public static void run(List<VigilantWrapper> vigilants, WrittenEvaluation writtenEvaluation, VigilantGroup group,
            ExamCoordinator coordinator, String emailMessage) {
        group.convokeVigilants(vigilants, writtenEvaluation);

        Set<Person> recievers = new HashSet<Person>();

        if (emailMessage.length() != 0) {
            Person person = coordinator.getPerson();
            for (VigilantWrapper vigilant : vigilants) {
                recievers.add(vigilant.getPerson());
            }

            String groupEmail = group.getContactEmail();
            String replyTo, bccs;

            recievers.addAll(writtenEvaluation.getTeachers());

            if (groupEmail != null) {
                bccs = groupEmail;
                replyTo = groupEmail;
            } else {
                bccs = null;
                replyTo = person.getEmail();
            }

            DateTime date = writtenEvaluation.getBeginningDateTime();
            String beginDateString = date.getDayOfMonth() + "/" + date.getMonthOfYear() + "/" + date.getYear();

            String subject =
                    BundleUtil.getString("resources.VigilancyResources", "email.convoke.subject",
                            group.getEmailSubjectPrefix(), writtenEvaluation.getName(), group.getName(), beginDateString);

            Message.from(person.getSender())
                    .replyTo(replyTo)
                    .to(Person.convertToUserGroup(recievers))
                    .singleBccs(bccs)
                    .subject(subject)
                    .textBody(emailMessage)
                    .send();
        }
    }
}