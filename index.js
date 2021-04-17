import { NativeModules } from 'react-native';

const { RNOpencv3 } = NativeModules;
const resolveAssetSource = require('react-native/Libraries/Image/resolveAssetSource');
const downloadAssetSource = require('./downloadAssetSource');


const useCascadeOnImage = (cascadeLocation, image) => {
	return new Promise(async (resolve, reject) => {
		// get the image in correct string format
		let finalImageUri = '';
		if (typeof image === 'string' && image.startsWith('file')) {
			finalImageUri = image.slice(6);
		} else {
			const sourceUri = await resolveAssetSource(image).uri;
			finalImageUri = await downloadAssetSource(sourceUri);
		}
		const srcMat = await RNOpencv3.imageToMat(finalImageUri);
		RNOpencv3.useCascadeOnImage(cascadeLocation, srcMat)
			.then((res) => {
				if (res === null || res === '') {
					resolve([]);
				}
				let objects = JSON.parse(res).objects;
				if (objects) {
					resolve(objects);
				} else {
					reject(res);
				}
			})
			.catch((err) => {
				reject(err);
			});
	});
};

export { useCascadeOnImage };
