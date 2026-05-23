import realDicomParser from '../../node_modules/dicom-parser/dist/dicomParser.min.js';

const actualParser = realDicomParser?.default || realDicomParser;

// Export default
export default actualParser;

// Export all individual named exports dynamically/statically
export const isStringVr = actualParser.isStringVr;
export const isPrivateTag = actualParser.isPrivateTag;
export const parsePN = actualParser.parsePN;
export const parseTM = actualParser.parseTM;
export const parseDA = actualParser.parseDA;
export const explicitElementToString = actualParser.explicitElementToString;
export const explicitDataSetToJS = actualParser.explicitDataSetToJS;
export const createJPEGBasicOffsetTable = actualParser.createJPEGBasicOffsetTable;
export const parseDicomDataSetExplicit = actualParser.parseDicomDataSetExplicit;
export const parseDicomDataSetImplicit = actualParser.parseDicomDataSetImplicit;
export const readFixedString = actualParser.readFixedString;
export const alloc = actualParser.alloc;
export const version = actualParser.version;
export const bigEndianByteArrayParser = actualParser.bigEndianByteArrayParser;
export const ByteStream = actualParser.ByteStream;
export const sharedCopy = actualParser.sharedCopy;
export const DataSet = actualParser.DataSet;
export const findAndSetUNElementLength = actualParser.findAndSetUNElementLength;
export const findEndOfEncapsulatedElement = actualParser.findEndOfEncapsulatedElement;
export const findItemDelimitationItemAndSetElementLength = actualParser.findItemDelimitationItemAndSetElementLength;
export const littleEndianByteArrayParser = actualParser.littleEndianByteArrayParser;
export const parseDicom = actualParser.parseDicom;
export const readDicomElementExplicit = actualParser.readDicomElementExplicit;
export const readDicomElementImplicit = actualParser.readDicomElementImplicit;
export const readEncapsulatedImageFrame = actualParser.readEncapsulatedImageFrame;
export const readEncapsulatedPixelData = actualParser.readEncapsulatedPixelData;
export const readEncapsulatedPixelDataFromFragments = actualParser.readEncapsulatedPixelDataFromFragments;
export const readPart10Header = actualParser.readPart10Header;
export const readSequenceItemsExplicit = actualParser.readSequenceItemsExplicit;
export const readSequenceItemsImplicit = actualParser.readSequenceItemsImplicit;
export const readSequenceItem = actualParser.readSequenceItem;
export const readTag = actualParser.readTag;
export const LEI = actualParser.LEI;
export const LEE = actualParser.LEE;
