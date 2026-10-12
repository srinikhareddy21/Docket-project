import { z } from 'zod';
import { STATUSES, JOB_TYPES, WORK_MODES } from '../config/constants';

const optionalUrl = z.string().trim().refine((v) => !v || /^https?:\/\/\S+\.\S+/.test(v), 'Enter a full link starting with http:// or https://').optional();

export const quickAddSchema = z.object({
  companyName: z.string().trim().min(1, 'Enter the company name').max(150),
  position: z.string().trim().min(1, 'Enter the role').max(150),
  applicationLink: optionalUrl,
  currentStatus: z.enum(STATUSES),
  applicationDate: z.string().optional(),
});

export const applicationSchema = z.object({
  companyName: z.string().trim().min(1, 'Enter the company name').max(150),
  position: z.string().trim().min(1, 'Enter the job title').max(150),
  jobType: z.enum(JOB_TYPES).or(z.literal('')),
  workMode: z.enum(WORK_MODES).or(z.literal('')),
  location: z.string().trim().max(150).optional(),
  jobDescription: z.string().max(20000).optional(),
  requiredSkills: z.string().max(2000).optional(),
  applicationSource: z.string().optional(),
  applicationDate: z.string().optional(),
  deadline: z.string().optional(),
  applicationLink: optionalUrl,
  salaryOrStipend: z.string().max(100).optional(),
  recruiterDetails: z.string().max(500).optional(),
  notes: z.string().max(10000).optional(),
  currentStatus: z.enum(STATUSES),
  resumeVersion: z.string().max(100).optional(),
  followUpDate: z.string().optional(),
}).refine((d) => !(d.applicationDate && d.deadline) || true, {});
