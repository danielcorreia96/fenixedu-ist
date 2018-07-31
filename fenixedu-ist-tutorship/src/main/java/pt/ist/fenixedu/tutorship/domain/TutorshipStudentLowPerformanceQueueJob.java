/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST Tutorship.
 *
 * FenixEdu IST Tutorship is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST Tutorship is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST Tutorship.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.tutorship.domain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.QueueJobResult;
import org.fenixedu.academic.domain.student.Registration;
import org.fenixedu.academic.domain.student.Student;
import org.fenixedu.academic.domain.student.registrationStates.RegistrationStateType;
import org.fenixedu.commons.spreadsheet.Spreadsheet;
import org.fenixedu.commons.spreadsheet.Spreadsheet.Row;
import org.joda.time.DateTime;

import pt.ist.fenixedu.tutorship.ui.Action.pedagogicalCouncil.studentLowPerformance.AbstractPrescriptionRule;
import pt.ist.fenixedu.tutorship.ui.Action.pedagogicalCouncil.studentLowPerformance.StudentLowPerformanceBean;
import pt.ist.fenixframework.Atomic;

public class TutorshipStudentLowPerformanceQueueJob extends TutorshipStudentLowPerformanceQueueJob_Base {

    public TutorshipStudentLowPerformanceQueueJob() {
        super();
    }

    public TutorshipStudentLowPerformanceQueueJob(PrescriptionEnum prescriptionEnum, ExecutionYear executionYear) {
        super();
        setPrescriptionEnum(prescriptionEnum);
        setExecutionYear(executionYear);
    }

    @Override
    public QueueJobResult execute() throws Exception {

        final List<StudentLowPerformanceBean> studentLowPerformanceBeans =
                calcstudentsLowPerformanceBean(AbstractPrescriptionRule.readPrescriptionRules(getPrescriptionEnum()));

        Spreadsheet spreadsheet = createSpreadsheet(studentLowPerformanceBeans);
        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();
        spreadsheet.exportToXLSSheet(byteArrayOS);

        final QueueJobResult queueJobResult = new QueueJobResult();

        queueJobResult.setContentType("application/txt");
        queueJobResult.setContent(byteArrayOS.toByteArray());

        return queueJobResult;

    }

    @Override
    public String getFilename() {
        return "Students_" + new DateTime().toString("yyyy-MM-dd") + ".xls";
    }

    private List<StudentLowPerformanceBean> calcstudentsLowPerformanceBean(List<AbstractPrescriptionRule> prescriptionRules) {
        LinkedList<StudentLowPerformanceBean> studentLowPerformanceBeans = new LinkedList<>();

        final List<AbstractPrescriptionRule> abstractPrescriptionRules =
                AbstractPrescriptionRule.readPrescriptionRules(getPrescriptionEnum());
        for (AbstractPrescriptionRule abstractPrescriptionRule : prescriptionRules) {

            for (Registration registration : abstractPrescriptionRule.getRegistrationStart(getExecutionYear()).getStudentsSet()) {
                if (isValidRegistration(registration)) {
                    StudentLowPerformanceBean s = calcStudentCycleLowPerformanceBean(registration, abstractPrescriptionRules);
                    if (s != null) {
                        studentLowPerformanceBeans.add(s);
                    }
                }

                else if (!registration.isBolonha() && isValidSourceLink(registration)) {
                    for (Registration destinationRegistration : registration.getDestinyRegistrationsSet()) {
                        if ((!destinationRegistration.getDegreeType().isBolonhaDegree())
                                && (!destinationRegistration.getDegreeType().isIntegratedMasterDegree())) {
                            continue;
                        }
                        if (!isValidRegistration(destinationRegistration)) {
                            continue;
                        }
                        StudentLowPerformanceBean s =
                                calcStudentCycleLowPerformanceBean(destinationRegistration, abstractPrescriptionRules);
                        if (s != null) {
                            studentLowPerformanceBeans.add(s);
                        }
                    }
                }
            }

        }
        return studentLowPerformanceBeans;
    }

    private StudentLowPerformanceBean calcStudentCycleLowPerformanceBean(Registration registration,
            List<AbstractPrescriptionRule> prescriptionRules) {

        int numberOfEntriesStudentInSecretary;

        List<Registration> fullRegistrationPath = getFullRegistrationPath(registration);

        // Historic Student
        numberOfEntriesStudentInSecretary = fullRegistrationPath.stream().mapToInt(this::getNumberOfEntriesStudentInSecretary).sum();

        BigDecimal sumEcts = registration.getCurriculum().getSumEctsCredits();

        if (isLowPerformanceStudent(registration, sumEcts, numberOfEntriesStudentInSecretary, prescriptionRules)) {
            Student student = registration.getStudent();
            String studentState = workingStudent(student);
            studentState += parcialStudent(registration);
            studentState += flunkedStudent(fullRegistrationPath);
            return new StudentLowPerformanceBean(student, sumEcts, registration.getDegree(), numberOfEntriesStudentInSecretary,
                    student.getPerson().getDefaultEmailAddressValue(), studentState, fullRegistrationPath.iterator().next()
                            .getStartDate().toString("yyyy-MM-dd"), registration.getIngressionType());
        }
        return null;
    }

    private int getNumberOfEntriesStudentInSecretary(Registration registration) {
        return (int) ExecutionYear.readExecutionYears(registration.getStartExecutionYear(), getExecutionYear())
                .stream()
                .map(registration::getLastRegistrationState)
                .filter(registrationState -> registrationState != null && registrationState.isActive())
                .count();
    }

    private boolean isLowPerformanceStudent(Registration registration, BigDecimal ects, int numberOfEntriesStudentInSecretary,
            List<AbstractPrescriptionRule> prescriptionRules) {
        return prescriptionRules.stream().anyMatch(prescriptionRule -> (prescriptionRule
                .isPrescript(registration, ects, numberOfEntriesStudentInSecretary, getExecutionYear())));
    }

    private String workingStudent(Student student) {
        return student.isWorkingStudent() ? "Trabalhor Estudante;" : "";
    }

    private String parcialStudent(Registration registration) {
        return (registration.isPartialRegime(getExecutionYear()) ? "Estudante Parcial;" : "");
    }

    private String flunkedStudent(List<Registration> registrations) {
        if (registrations.stream()
                .flatMap(registration -> registration.getRegistrationStatesSet().stream())
                .anyMatch(registrationState -> registrationState.getStateType() == RegistrationStateType.FLUNKED)) {
            return "Prescrito;";
        }
        return "";
    }

    private Spreadsheet createSpreadsheet(List<StudentLowPerformanceBean> studentLowPerformanceBeans) throws IOException {
        Spreadsheet spreadsheet = new Spreadsheet(getFilename());

        spreadsheet.setHeader("Nome");
        spreadsheet.setHeader("Número");
        spreadsheet.setHeader("Nome do Curso");
        spreadsheet.setHeader("Ciclo");
        spreadsheet.setHeader("Créditos Alcançados");
        spreadsheet.setHeader("Regime");
        spreadsheet.setHeader("Email");
        spreadsheet.setHeader("Número de Inscrições");
        spreadsheet.setHeader("Início da Matrícula");
        spreadsheet.setHeader("Tipo Ingresso");
        spreadsheet.setHeader("Tutor");
        spreadsheet.setHeader("Email do Tutor");

        for (StudentLowPerformanceBean studentLowPerformanceBean : studentLowPerformanceBeans) {
            final Row row = spreadsheet.addRow();
            row.setCell(studentLowPerformanceBean.getStudent().getName());
            row.setCell(studentLowPerformanceBean.getStudent().getNumber().toString());
            row.setCell(studentLowPerformanceBean.getDegree().getName());
            row.setCell(studentLowPerformanceBean.getDegree().getDegreeType().getName().getContent());
            row.setCell(studentLowPerformanceBean.getSumEcts().toString());
            row.setCell(studentLowPerformanceBean.getRegime());
            row.setCell(studentLowPerformanceBean.getEmail());
            row.setCell(studentLowPerformanceBean.getNumberOfEntriesStudentInSecretary());
            row.setCell(studentLowPerformanceBean.getRegistrationStart());
            row.setCell(studentLowPerformanceBean.getIngressionType() != null ? studentLowPerformanceBean.getIngressionType()
                    .getLocalizedName() : "");
            List<Tutorship> tutorships = Tutorship.getActiveTutorships(studentLowPerformanceBean.getStudent());
            if (!tutorships.isEmpty()) {
                row.setCell(tutorships.iterator().next().getTeacher().getPerson().getPresentationName());
                row.setCell(tutorships.iterator().next().getTeacher().getPerson().getInstitutionalOrDefaultEmailAddressValue());
            } else {
                row.setCell("");
                row.setCell("");
            }
        }

        return spreadsheet;
    }

    // Historic Student
    protected static List<Registration> getFullRegistrationPath(final Registration current) {
        if (current.getDegreeType().isBolonhaDegree() || current.getDegreeType().isIntegratedMasterDegree()) {
            List<Registration> path = new ArrayList<>();
            path.add(current);
            Registration source;
            if (current.getSourceRegistration() != null
                    && (!(source = current.getSourceRegistration()).isBolonha() || isValidSourceLink(source))) {
                path.addAll(getFullRegistrationPath(source));
            }

            path.sort(Registration.COMPARATOR_BY_START_DATE);
            return path;
        } else {
            return Collections.singletonList(current);
        }
    }

    protected static boolean isValidSourceLink(Registration source) {
        return Stream.of(RegistrationStateType.TRANSITED, RegistrationStateType.FLUNKED, RegistrationStateType.INTERNAL_ABANDON,
                RegistrationStateType.EXTERNAL_ABANDON, RegistrationStateType.INTERRUPTED)
                .anyMatch(registrationStateType -> source.getActiveStateType() == registrationStateType);
    }

    private boolean isValidRegistration(Registration registration) {
        return registration.isBolonha() && registration.isActive()
                && (registration.getDegreeType().isFirstCycle() || registration.getDegreeType().isSecondCycle())
                && !registration.getDegreeType().isEmpty();
    }

    @Atomic
    public static TutorshipStudentLowPerformanceQueueJob createTutorshipStudentLowPerformanceQueueJob(
            PrescriptionEnum prescriptionEnum, ExecutionYear executionYear) {
        return new TutorshipStudentLowPerformanceQueueJob(prescriptionEnum, executionYear);
    }

}
