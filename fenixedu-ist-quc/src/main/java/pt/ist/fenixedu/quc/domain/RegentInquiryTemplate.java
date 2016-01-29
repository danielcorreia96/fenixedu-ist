/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST QUC.
 *
 * FenixEdu IST QUC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST QUC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST QUC.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.quc.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.fenixedu.academic.domain.ExecutionCourse;
import org.fenixedu.academic.domain.ExecutionSemester;
import org.fenixedu.academic.domain.Person;
import org.fenixedu.academic.domain.Professorship;
import org.fenixedu.bennu.core.domain.Bennu;
import org.joda.time.DateTime;

import com.google.common.collect.Sets;

public class RegentInquiryTemplate extends RegentInquiryTemplate_Base {

    public RegentInquiryTemplate(DateTime begin, DateTime end) {
        super();
        init(begin, end);
    }

    public static RegentInquiryTemplate getTemplateByExecutionPeriod(ExecutionSemester executionSemester) {
        final Collection<InquiryTemplate> inquiryTemplates = Bennu.getInstance().getInquiryTemplatesSet();
        for (final InquiryTemplate inquiryTemplate : inquiryTemplates) {
            if (inquiryTemplate instanceof RegentInquiryTemplate && executionSemester == inquiryTemplate.getExecutionPeriod()) {
                return (RegentInquiryTemplate) inquiryTemplate;
            }
        }
        return null;
    }

    public static RegentInquiryTemplate getCurrentTemplate() {
        final Collection<InquiryTemplate> inquiryTemplates = Bennu.getInstance().getInquiryTemplatesSet();
        for (final InquiryTemplate inquiryTemplate : inquiryTemplates) {
            if (inquiryTemplate instanceof RegentInquiryTemplate && inquiryTemplate.isOpen()) {
                return (RegentInquiryTemplate) inquiryTemplate;
            }
        }
        return null;
    }

    public static Collection<ExecutionCourse> getExecutionCoursesWithRegentInquiriesToAnswer(Person person) {
        final Set<ExecutionCourse> result = new HashSet<ExecutionCourse>();
        final Set<ExecutionCourse> allExecutionCourses = new HashSet<ExecutionCourse>();
        final RegentInquiryTemplate currentTemplate = getCurrentTemplate();
        if (currentTemplate != null) {
            for (final Professorship professorship : person.getProfessorships(currentTemplate.getExecutionPeriod())) {
                final boolean isToAnswer = hasToAnswerRegentInquiry(professorship);
                if (isToAnswer) {
                    allExecutionCourses.add(professorship.getExecutionCourse());
                    if (professorship.getInquiryRegentAnswer() == null
                            || professorship.getInquiryRegentAnswer().hasRequiredQuestionsToAnswer(currentTemplate)
                            || InquiryResultComment.hasMandatoryCommentsToMakeAsResponsible(professorship)) {
                        result.add(professorship.getExecutionCourse());
                    }
                }
            }
            final Collection<ExecutionCourse> disjunctionEC = Sets.symmetricDifference(result, allExecutionCourses);
            for (final ExecutionCourse executionCourse : disjunctionEC) {
                if (InquiryResultComment.hasMandatoryCommentsToMakeAsRegentInUC(person, executionCourse)) {
                    result.add(executionCourse);
                }
            }
        }
        return result;
    }

    public static boolean hasToAnswerRegentInquiry(Professorship professorship) {
        return professorship.getResponsibleFor() && InquiriesRoot.isAvailableForInquiry(professorship.getExecutionCourse());
    }
}
